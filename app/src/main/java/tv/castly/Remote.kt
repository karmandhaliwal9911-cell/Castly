package tv.castly

import androidx.compose.runtime.MutableState
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/** One remote, three kinds of TV. The Remote screen only talks to this, so the side panel and screens stay the same. */
interface Remote {
    val status: MutableState<String>                   // shown in Settings > TV remote while pairing
    fun key(n: String)                                 // UP DOWN LEFT RIGHT OK ENTER BACK HOME VOL_UP VOL_DOWN MUTE
    fun click()
    fun text(full: String)
    fun move(dx: Int, dy: Int)
    fun scroll(dy: Int)
    fun close() {}
}

/** Samsung or LG remote for the TV address saved in Settings. Android TV uses RemoteClient. */
fun makeRemote(p: Prefs): Remote? = when (p.brand.value) {
    "samsung" -> SamsungRemote(p.tvIp.value, p)
    "lg" -> LgRemote(p.tvIp.value, p)
    else -> null
}

/** Samsung and LG TVs use self-signed certificates. This is only used for the TV address typed in Settings. */
fun trustAll(): SSLSocketFactory = SSLContext.getInstance("TLS").apply {
    init(null, arrayOf(object : X509TrustManager {
        override fun checkClientTrusted(c: Array<X509Certificate>?, a: String?) {}
        override fun checkServerTrusted(c: Array<X509Certificate>?, a: String?) {}
        override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
    }), null)
}.socketFactory
