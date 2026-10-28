package tv.castly

import android.util.Base64
import androidx.compose.runtime.mutableStateOf
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI

/** Samsung Smart TV (Tizen, about 2016 and newer). First connection shows "Allow?" on the TV; the token is saved for next time. */
class SamsungRemote(private val ip: String, private val prefs: Prefs) : Remote {
    override val status = mutableStateOf("Connecting\u2026")
    private var ws: WebSocketClient? = null
    private var x = 960; private var y = 540; private var acc = 0

    init {
        val name = Base64.encodeToString("Castly".toByteArray(), Base64.NO_WRAP)
        val token = prefs.getKey("samsung_token")?.let { "&token=$it" } ?: ""
        ws = object : WebSocketClient(URI("wss://$ip:8002/api/v2/channels/samsung.remote.control?name=$name$token")) {
            override fun onOpen(h: ServerHandshake?) { status.value = "Accept the prompt on the TV\u2026" }
            override fun onMessage(m: String?) {
                val j = JSONObject(m ?: return)
                when (j.optString("event")) {
                    "ms.channel.connect" -> { j.optJSONObject("data")?.optString("token")?.takeIf { it.isNotEmpty() }?.let { prefs.putKey("samsung_token", it) }
                        status.value = "Paired \u2713" }
                    "ms.channel.timeOut", "ms.error" -> status.value = "The TV said no. Try again and choose Allow."
                }
            }
            override fun onClose(c: Int, r: String?, remote: Boolean) { if (!status.value.startsWith("Paired")) status.value = "Not connected" }
            override fun onError(e: Exception?) { status.value = "Can't reach the TV. Check the IP address and Wi\u2011Fi." }
        }.also { it.setSocketFactory(trustAll()); it.connect() }
    }

    private fun send(params: JSONObject) { ws?.takeIf { it.isOpen }?.send(JSONObject().put("method", "ms.remote.control").put("params", params).toString()) }
    private fun k(code: String) = send(JSONObject().put("Cmd", "Click").put("DataOfCmd", code).put("Option", "false").put("TypeOfRemote", "SendRemoteKey"))

    override fun key(n: String) = k(mapOf("UP" to "KEY_UP", "DOWN" to "KEY_DOWN", "LEFT" to "KEY_LEFT", "RIGHT" to "KEY_RIGHT", "OK" to "KEY_ENTER",
        "ENTER" to "KEY_ENTER", "BACK" to "KEY_RETURN", "HOME" to "KEY_HOME", "VOL_UP" to "KEY_VOLUP", "VOL_DOWN" to "KEY_VOLDOWN", "MUTE" to "KEY_MUTE")[n] ?: return)
    override fun click() = k("KEY_ENTER")                                  // Enter clicks whatever the pointer is on
    override fun move(dx: Int, dy: Int) {
        x = (x + dx).coerceIn(0, 1920); y = (y + dy).coerceIn(0, 1080)
        send(JSONObject().put("Cmd", "Move").put("Position", JSONObject().put("x", x).put("y", y).put("Time", "0")).put("TypeOfRemote", "ProcessMouseDevice"))
    }
    override fun scroll(dy: Int) { acc += dy; if (Math.abs(acc) >= 50) { k(if (acc > 0) "KEY_UP" else "KEY_DOWN"); acc = 0 } }   // Samsung has no wheel, so steps
    override fun text(full: String) = send(JSONObject().put("Cmd", Base64.encodeToString(full.toByteArray(), Base64.NO_WRAP)).put("DataOfCmd", "base64").put("TypeOfRemote", "SendInputString"))
    override fun close() { runCatching { ws?.close() } }
}
