package tv.castly

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArraySet

/** Serves the TV page on :8080 and the stream on :8081 (only after the right code). */
class MirrorServer(ctx: Context, private val code: String, private val onViewers: (Int) -> Unit) {
    private val page = ctx.assets.open("index.html").readBytes()
    private val viewers = CopyOnWriteArraySet<WebSocket>()

    private val http = object : NanoHTTPD(8080) {
        override fun serve(s: IHTTPSession): Response =
            newFixedLengthResponse(Response.Status.OK, "text/html", page.inputStream(), page.size.toLong())
    }

    private val ws = object : WebSocketServer(InetSocketAddress(8081)) {
        override fun onOpen(c: WebSocket, h: ClientHandshake) {
            if (h.resourceDescriptor.contains("code=$code")) { c.setAttachment(true); viewers.add(c); onViewers(viewers.size) }
            else c.close(1008, "bad code")
        }
        override fun onClose(c: WebSocket, i: Int, r: String?, remote: Boolean) { viewers.remove(c); onViewers(viewers.size) }
        override fun onMessage(c: WebSocket, m: String?) {}
        override fun onError(c: WebSocket?, e: Exception) {}
        override fun onStart() { connectionLostTimeout = 5 }
    }

    fun start() { http.start(); ws.isReuseAddr = true; ws.start() }
    fun stop() { http.stop(); ws.stop(500) }

    /** Frame layout: [type:1][timestamp µs:8 float64][payload]. 1=key, 2=delta, 3=PCM audio. */
    fun send(type: Int, tsUs: Long, payload: ByteArray) {
        if (viewers.isEmpty()) return
        val b = ByteBuffer.allocate(9 + payload.size).put(type.toByte()).putDouble(tsUs.toDouble()).put(payload).flip()
        for (v in viewers) if (v.isOpen) v.send(b.duplicate())
    }
}
