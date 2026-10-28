package tv.castly

import androidx.compose.runtime.mutableStateOf
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** LG webOS TV. First connection shows a prompt on the TV; the key is saved for next time. Pointer and scroll use LG's own mouse channel. */
class LgRemote(private val ip: String, private val prefs: Prefs) : Remote {
    override val status = mutableStateOf("Connecting\u2026")
    private var main: WebSocketClient? = null
    private var ptr: WebSocketClient? = null
    private var muted = false

    init { open(true) }

    private fun open(secure: Boolean) {                                   // newer TVs: wss 3001, older: ws 3000
        main = object : WebSocketClient(URI(if (secure) "wss://$ip:3001" else "ws://$ip:3000")) {
            override fun onOpen(h: ServerHandshake?) {
                status.value = "Accept the prompt on the TV\u2026"
                val payload = JSONObject().put("forcePairing", false).put("pairingType", "PROMPT").put("manifest", JSONObject().put("manifestVersion", 1)
                    .put("permissions", JSONArray(listOf("CONTROL_AUDIO", "CONTROL_INPUT_TEXT", "CONTROL_MOUSE_AND_KEYBOARD", "CONTROL_INPUT_TV"))))
                prefs.getKey("lg_key")?.let { payload.put("client-key", it) }
                send(JSONObject().put("type", "register").put("id", "reg").put("payload", payload).toString())
            }
            override fun onMessage(m: String?) {
                val j = JSONObject(m ?: return)
                when (j.optString("type")) {
                    "registered" -> { j.getJSONObject("payload").optString("client-key").takeIf { it.isNotEmpty() }?.let { prefs.putKey("lg_key", it) }
                        status.value = "Paired \u2713"; req("sock", "ssap://com.webos.service.networkinput/getPointerInputSocket") }
                    "response" -> if (j.optString("id") == "sock") j.optJSONObject("payload")?.optString("socketPath")?.takeIf { it.isNotEmpty() }?.let { pointer(it) }
                    "error" -> status.value = "The TV said no. Try again and choose Accept."
                }
            }
            override fun onClose(c: Int, r: String?, remote: Boolean) { if (!status.value.startsWith("Paired")) status.value = "Not connected" }
            override fun onError(e: Exception?) { if (secure && !status.value.startsWith("Paired")) open(false) else status.value = "Can't reach the TV. Check the IP address and Wi\u2011Fi." }
        }.also { if (secure) it.setSocketFactory(trustAll()); it.connect() }
    }

    private fun pointer(path: String) {
        ptr = object : WebSocketClient(URI(path)) {
            override fun onOpen(h: ServerHandshake?) {}
            override fun onMessage(m: String?) {}
            override fun onClose(c: Int, r: String?, remote: Boolean) {}
            override fun onError(e: Exception?) {}
        }.also { if (path.startsWith("wss")) it.setSocketFactory(trustAll()); it.connect() }
    }

    private fun req(id: String, uri: String, payload: JSONObject? = null) {
        main?.takeIf { it.isOpen }?.send(JSONObject().put("type", "request").put("id", id).put("uri", uri).apply { if (payload != null) put("payload", payload) }.toString())
    }
    private fun p(s: String) { ptr?.takeIf { it.isOpen }?.send(s) }

    override fun key(n: String) = when (n) {
        "VOL_UP" -> req("v", "ssap://audio/volumeUp")
        "VOL_DOWN" -> req("v", "ssap://audio/volumeDown")
        "MUTE" -> { muted = !muted; req("m", "ssap://audio/setMute", JSONObject().put("mute", muted)) }
        "OK", "ENTER" -> p("type:button\nname:ENTER\n\n")
        else -> p("type:button\nname:$n\n\n")                             // UP DOWN LEFT RIGHT BACK HOME
    }
    override fun click() = p("type:click\n\n")
    override fun move(dx: Int, dy: Int) = p("type:move\ndx:$dx\ndy:$dy\ndown:0\n\n")
    override fun scroll(dy: Int) = p("type:scroll\ndx:0\ndy:$dy\n\n")      // real wheel scrolling
    override fun text(full: String) = req("t", "ssap://com.webos.service.ime/insertText", JSONObject().put("text", full).put("replace", 1))   // needs a text box open on the TV
    override fun close() { runCatching { main?.close(); ptr?.close() } }
}
