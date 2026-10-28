package tv.castly

import android.content.Context
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.URL
import kotlin.concurrent.thread

/** Phone side: finds Castly TV apps on the Wi-Fi (no code) and asks the chosen TV to start playing. */
class TvFinder(ctx: Context, private val onFound: (Tv) -> Unit, private val onLost: (String) -> Unit) {
    data class Tv(val name: String, val host: String, val port: Int)
    private val nsd = ctx.getSystemService(NsdManager::class.java)

    private val listener = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(s: NsdServiceInfo) {
            nsd.resolveService(s, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, c: Int) {}
                override fun onServiceResolved(i: NsdServiceInfo) { onFound(Tv(i.serviceName, i.host.hostAddress!!, i.port)) }
            })
        }
        override fun onServiceLost(s: NsdServiceInfo) { onLost(s.serviceName) }
        override fun onDiscoveryStarted(t: String) {}
        override fun onDiscoveryStopped(t: String) {}
        override fun onStartDiscoveryFailed(t: String, c: Int) {}
        override fun onStopDiscoveryFailed(t: String, c: Int) {}
    }

    fun start() = nsd.discoverServices("_castly._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
    fun stop() = runCatching { nsd.stopServiceDiscovery(listener) }

    /** Asks the Castly TV app to share ITS screen to this phone (the TV shows "Allow?"), and later to stop. */
    fun mirrorRequest(tv: Tv, phoneIp: String, code: String, phoneName: String) = thread {
        runCatching { URL("http://${tv.host}:${tv.port}/mirror?host=$phoneIp&code=$code&name=${Uri.encode(phoneName)}").openConnection().apply { connectTimeout = 3000 }.getInputStream().close() } }
    fun mirrorStop(tv: Tv) = thread { runCatching { URL("http://${tv.host}:${tv.port}/mirrorstop").openConnection().apply { connectTimeout = 3000 }.getInputStream().close() } }

    /** Call after MirrorService is running. The TV shows "Allow?" and then opens http://phoneIp:8080. */
    fun pair(tv: Tv, phoneIp: String, code: String, phoneName: String, port: Int = 8080) = thread {
        runCatching {
            URL("http://${tv.host}:${tv.port}/pair?host=$phoneIp&code=$code&name=${Uri.encode(phoneName)}&port=$port")
                .openConnection().apply { connectTimeout = 3000 }.getInputStream().close()
        }
    }
}
