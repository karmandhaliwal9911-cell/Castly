package tv.castly

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.codescanner.GmsBarcodeScannerOptions
import com.google.android.gms.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

class MainActivity : ComponentActivity() {
    private val st = CastState()
    private val rc = RemoteClient()
    private lateinit var prefs: Prefs
    private lateinit var finder: TvFinder
    private var wholeScreen = true

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { askProjection() }
    private val projection = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data ?: return@registerForActivityResult
        if (r.resultCode != RESULT_OK) return@registerForActivityResult
        startForegroundService(Intent(this, MirrorService::class.java)
            .putExtra("result", r.resultCode).putExtra("data", data).putExtra("code", st.code)
            .putExtra("fps", prefs.fps.intValue).putExtra("mbps", prefs.mbps.intValue)
            .putExtra("short_side", prefs.side.intValue).putExtra("audio", prefs.audio.value))
        st.tv?.takeIf { it.host.isNotEmpty() }?.let { finder.pair(it, MirrorService.phoneIp(), st.code, Build.MODEL) }
        st.relayCode?.let { Relay.join(it, "http://${MirrorService.phoneIp()}:8080/?code=${st.code}") }
        st.live = true
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        finder = TvFinder(this,
            { tv -> runOnUiThread { if (st.found.none { it.name == tv.name }) st.found.add(tv) } },
            { n -> runOnUiThread { st.found.removeAll { it.name == n } } })
        setContent { CastlyTheme(prefs) { CastlyApp(st, prefs, ::start, ::stop, rc, ::scan, ::shareToTv, ::stopShare, ::sendToTv, ::stopSend, ::mirrorTv, ::stopMirrorTv, ::findViaBluetooth) } }
    }

    override fun onResume() { super.onResume(); finder.start() }
    override fun onPause() { finder.stop(); super.onPause() }

    private fun start(whole: Boolean) {
        wholeScreen = whole
        val need = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isEmpty()) askProjection() else perms.launch(need.toTypedArray())
    }

    private fun askProjection() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        projection.launch(
            if (Build.VERSION.SDK_INT >= 34) mpm.createScreenCaptureIntent(
                if (wholeScreen) MediaProjectionConfig.createConfigForDefaultDisplay()
                else MediaProjectionConfig.createConfigForUserChoice())   // Android 14+: user can pick one app
            else mpm.createScreenCaptureIntent())
    }

    /** Google's code scanner opens its own camera screen (no camera permission needed). The TV's QR holds only its address. */
    private fun scan(done: () -> Unit) {
        GmsBarcodeScanning.getClient(this, GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
            .startScan().addOnSuccessListener { b ->
                val u = Uri.parse(b.rawValue ?: return@addOnSuccessListener)
                val h = u.getQueryParameter("h"); val p = u.getQueryParameter("p")?.toIntOrNull()
                val ok = u.scheme == "castly" && u.host == "pair" && h != null && p != null &&
                    Regex("^(10\\.|192\\.168\\.|172\\.(1[6-9]|2\\d|3[01])\\.)[0-9.]+$").matches(h)   // home-network addresses only
                if (ok) { st.relayCode = null; st.tv = TvFinder.Tv(u.getQueryParameter("n") ?: "TV", h!!, p!!); done() }
                else if (u.scheme == "castly" && u.host == "join") {          // QR shown by the castly.tv website
                    val c = u.getQueryParameter("c")
                    if (c != null && c.length == 6 && c.all(Char::isDigit)) { st.tv = TvFinder.Tv("TV browser", "", 0); st.relayCode = c; done() }
                }
            }
    }

    /** Starts the media server (port 8082), then asks the TV to open it. TV app: "Allow?" prompt. TV website: via the relay. */
    private fun shareToTv() {
        val tv = st.tv ?: return
        Send.active = false
        startForegroundService(Intent(this, MediaService::class.java))
        val ip = MirrorService.phoneIp()
        if (tv.host.isNotEmpty()) finder.pair(tv, ip, Share.code, Build.MODEL, 8082)
        else st.relayCode?.let { Relay.join(it, "http://$ip:8082/?code=${Share.code}") }
    }
    /** Same server as sharing media, but serves the picked files as downloads (page /send). */
    private fun sendToTv() {
        val tv = st.tv ?: return
        Share.active = false; Share.code = CastState.newCode()
        startForegroundService(Intent(this, MediaService::class.java))
        val ip = MirrorService.phoneIp()
        if (tv.host.isNotEmpty()) finder.pair(tv, ip, Share.code, Build.MODEL, 8082)
        else st.relayCode?.let { Relay.join(it, "http://$ip:8082/?code=${Share.code}") }
    }
    private fun stopSend() { stopService(Intent(this, MediaService::class.java)); Send.active = false }

    /** TV screen on the phone: tell the TV app (it asks "Allow?"), and keep trying to connect to its stream. */
    private fun mirrorTv() { val tv = st.tv ?: return; val code = CastState.newCode(); TvView.start(tv.host, code); finder.mirrorRequest(tv, MirrorService.phoneIp(), code, Build.MODEL) }
    private fun stopMirrorTv() { st.tv?.takeIf { it.host.isNotEmpty() }?.let { finder.mirrorStop(it) }; TvView.stop() }

    private val ble by lazy { BleFinder(this) { tv -> runOnUiThread { if (st.btFound.none { it.host == tv.host }) st.btFound.add(tv) } } }
    private val blePerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> if (r.values.all { it }) startBle() }
    private fun findViaBluetooth() {
        val need = (if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else listOf(Manifest.permission.ACCESS_FINE_LOCATION))
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isEmpty()) startBle() else blePerms.launch(need.toTypedArray())
    }
    private fun startBle() { st.btFound.clear(); st.btScanning = true; ble.start { st.btScanning = false } }

    private fun stopShare() { stopService(Intent(this, MediaService::class.java)); Share.active = false }

    private fun stop() { stopService(Intent(this, MirrorService::class.java)); st.live = false; st.code = CastState.newCode() }
}
