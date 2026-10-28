package tv.castly

import android.net.Uri
import java.net.*
import java.util.concurrent.Executors

/** Phone -> TV remote. Keys, click and typing go over HTTP (:8090). Pointer moves and scroll go over UDP (:8091) so they stay instant. */
class RemoteClient : Remote {
    override val status = androidx.compose.runtime.mutableStateOf("Ready")
    private var host = ""
    private val web = Executors.newSingleThreadExecutor()
    private val fast = Executors.newSingleThreadExecutor()
    private val sock by lazy { DatagramSocket() }

    fun connect(tvHost: String) { host = tvHost }
    override fun key(n: String) = http("/key?n=$n")
    override fun click() = http("/click")
    override fun text(full: String) = http("/text?v=${Uri.encode(full)}")          // whole text box, so Delete just works
    override fun move(dx: Int, dy: Int) = udp("m $dx $dy")
    override fun scroll(dy: Int) = udp("s $dy")

    private fun http(path: String) { if (host.isEmpty()) return
        web.execute { runCatching { (URL("http://$host:8090$path").openConnection() as HttpURLConnection)
            .apply { connectTimeout = 800; readTimeout = 800 }.inputStream.close() } } }
    private fun udp(m: String) { if (host.isEmpty()) return
        fast.execute { runCatching { val b = m.toByteArray(); sock.send(DatagramPacket(b, b.size, InetAddress.getByName(host), 8091)) } } }
}
