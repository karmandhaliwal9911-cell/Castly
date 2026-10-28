package tv.castly.tv

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.graphics.Bitmap
import android.net.Uri
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.Manifest
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.URLUtil
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream
import java.net.NetworkInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import fi.iki.elonen.NanoHTTPD

/** Castly TV app: advertises this TV on the Wi-Fi, asks "Allow?", then plays the phone's stream full screen. */
class TvActivity : Activity() {
    private lateinit var web: WebView
    private var pairServer: NanoHTTPD? = null
    private val nsd by lazy { getSystemService(NsdManager::class.java) }
    private val regListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(i: NsdServiceInfo) {}
        override fun onRegistrationFailed(i: NsdServiceInfo, c: Int) {}
        override fun onServiceUnregistered(i: NsdServiceInfo) {}
        override fun onUnregistrationFailed(i: NsdServiceInfo, c: Int) {}
    }
    private val privateIp = Regex("^(10\\.|192\\.168\\.|172\\.(1[6-9]|2\\d|3[01])\\.)[0-9.]+$")

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false   // lets sound start without a tap
            setDownloadListener { url, _, cd, mime, _ -> download(url, cd, mime) }       // files sent from the phone
        }
        setContentView(web)
        val f = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(dlReceiver, f, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(dlReceiver, f)
        showIdle()
        if (bleAllowed()) startBle() else requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT), 9)

        pairServer = object : NanoHTTPD(8090) {                  // the phone calls this after you tap the TV
            override fun serve(s: IHTTPSession): Response {
                if (s.uri == "/mirror") {                                 // phone wants to watch this TV's screen
                    val q = s.parameters; val host = q["host"]?.firstOrNull(); val code = q["code"]?.firstOrNull()
                    if (host == null || code == null || !privateIp.matches(host) || code.length != 6) return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "no")
                    runOnUiThread { mirrorHost = host; askMirror(q["name"]?.firstOrNull() ?: "A phone", code) }
                    return newFixedLengthResponse("ok")
                }
                if (s.uri == "/mirrorstop") {
                    if (s.remoteIpAddress != mirrorHost) return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "no")
                    runOnUiThread { stopService(Intent(this@TvActivity, MirrorService::class.java)) }
                    return newFixedLengthResponse("ok")
                }
                if (s.uri != "/pair") {                                   // remote commands, only from the allowed phone
                    if (s.remoteIpAddress != RemoteState.allowed) return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "no")
                    val r = RemoteService.i ?: return newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, "text/plain", "Turn on Castly Remote in TV Accessibility settings")
                    val q = s.parameters
                    when (s.uri) { "/key" -> r.key(q["n"]?.firstOrNull() ?: ""); "/click" -> r.click(); "/text" -> r.text(q["v"]?.firstOrNull() ?: "") }
                    return newFixedLengthResponse("ok")
                }
                val p = s.parameters
                val host = p["host"]?.firstOrNull(); val code = p["code"]?.firstOrNull()
                val name = p["name"]?.firstOrNull() ?: "A phone"
                val port = p["port"]?.firstOrNull()?.takeIf { it == "8082" } ?: "8080"      // 8080 = screen, 8082 = shared files
                if (s.uri != "/pair" || host == null || code == null || !privateIp.matches(host) || code.length != 6)
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "no")
                runOnUiThread { askAllow(name, host, code, port) }
                return newFixedLengthResponse("ok")
            }
        }.also { it.start() }

        nsd.registerService(NsdServiceInfo().apply {
            serviceName = Build.MODEL; serviceType = "_castly._tcp."; port = 8090
        }, NsdManager.PROTOCOL_DNS_SD, regListener)
    }

    private fun askAllow(name: String, host: String, code: String, port: String) {
        AlertDialog.Builder(this).setTitle("Allow $name to show content on this TV?")
            .setPositiveButton("Allow") { _, _ -> RemoteState.allowed = host; web.loadUrl("http://$host:$port/?code=$code") }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showIdle() = web.loadDataWithBaseURL(null,
        "<body style='margin:0;background:#000;color:#eef1f6;font:36px sans-serif;display:grid;place-items:center;height:100vh;text-align:center'>" +
        "<div><h1>Castly</h1>Open Castly on your phone and pick<br><b style='color:#5eead4'>${Build.MODEL}</b><br><img src='${qrDataUri()}' style='width:260px;border-radius:16px;margin-top:24px'><br><span style='font-size:24px;color:#8a94a6'>Or scan this QR code in the Castly app</span></div>",
        "text/html", "utf-8", null)

    private var ble: BleBeacon? = null
    private fun localIp() = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
        .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }?.hostAddress ?: ""
    private fun startBle() {                                                  // phones can also find this TV with Bluetooth
        ble = BleBeacon(this) { """{"n":"${Build.MODEL}","h":"${localIp()}","p":8090}""" }.also { it.start() }
    }
    private fun bleAllowed() = Build.VERSION.SDK_INT < 31 || (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
    private var mirrorHost: String? = null
    private var mirrorCode = ""
    private fun askMirror(name: String, code: String) {
        AlertDialog.Builder(this).setTitle("Allow $name to see this TV's screen?")
            .setPositiveButton("Allow") { _, _ -> mirrorCode = code
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) askCapture() else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 7) }
            .setNegativeButton("Cancel", null).show()
    }
    private fun askCapture() {
        try { startActivityForResult(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(), 8) }
        catch (e: Exception) { Toast.makeText(this, "This TV does not allow screen capture", Toast.LENGTH_LONG).show() }
    }
    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) { super.onRequestPermissionsResult(rc, p, r); if (rc == 7) askCapture(); if (rc == 9 && bleAllowed()) startBle() }
    @Deprecated("TV capture result")
    override fun onActivityResult(rq: Int, rs: Int, d: Intent?) {
        super.onActivityResult(rq, rs, d)
        if (rq == 8 && rs == RESULT_OK && d != null) startForegroundService(Intent(this, MirrorService::class.java).putExtra("result", rs).putExtra("data", d)
            .putExtra("code", mirrorCode).putExtra("fps", 30).putExtra("mbps", 8).putExtra("short_side", 720).putExtra("audio", true))
    }

    private val pending = mutableMapOf<Long, String>()
    private var waitingApk: File? = null
    private val dlReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val name = pending.remove(i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)) ?: return
            Toast.makeText(this@TvActivity, "Saved $name", Toast.LENGTH_LONG).show()
            if (name.endsWith(".apk", true)) install(File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name))
        }
    }

    private fun download(url: String, cd: String?, mime: String?) {
        val name = URLUtil.guessFileName(url, cd, mime)
        val dm = getSystemService(DownloadManager::class.java)
        pending[dm.enqueue(DownloadManager.Request(Uri.parse(url)).setTitle(name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, name))] = name
        Toast.makeText(this, "Downloading $name", Toast.LENGTH_SHORT).show()
    }

    /** APKs: opens Android's installer. The first time, the TV asks you to allow Castly to install apps. */
    private fun install(f: File) {
        if (!packageManager.canRequestPackageInstalls()) {
            waitingApk = f
            Toast.makeText(this, "Allow Castly to install apps, then press Back", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))); return
        }
        startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(FileProvider.getUriForFile(this, "$packageName.files", f), "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onResume() { super.onResume(); waitingApk?.takeIf { packageManager.canRequestPackageInstalls() }?.let { waitingApk = null; install(it) } }

    /** QR holds only this TV's address; the phone still has to be allowed with the "Allow?" prompt. */
    private fun qrDataUri(): String {
        val ip = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }?.hostAddress ?: return ""
        val m = QRCodeWriter().encode("castly://pair?h=$ip&p=8090&n=${Uri.encode(Build.MODEL)}", BarcodeFormat.QR_CODE, 360, 360, mapOf(EncodeHintType.MARGIN to 2))
        val bmp = Bitmap.createBitmap(360, 360, Bitmap.Config.ARGB_8888)
        for (x in 0 until 360) for (y in 0 until 360) bmp.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        val o = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.PNG, 100, o)
        return "data:image/png;base64," + Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP)
    }

    @Deprecated("TV remote Back")
    override fun onBackPressed() { if (web.url?.startsWith("http") == true) showIdle() else finish() }

    override fun onDestroy() {
        ble?.stop(); runCatching { unregisterReceiver(dlReceiver) }; pairServer?.stop(); runCatching { nsd.unregisterService(regListener) }; web.destroy(); super.onDestroy()
    }
}
