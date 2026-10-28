package tv.castly

import android.media.*
import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/** Shows the TV's screen on the phone: receives H.264 + sound from the Castly TV app (port 8081) and plays it. */
object TvView {
    var status by mutableStateOf("Not started")
    var active by mutableStateOf(false)
    private var ws: WebSocketClient? = null
    private var dec: MediaCodec? = null
    private var track: AudioTrack? = null
    @Volatile private var surface: Surface? = null
    @Volatile private var run = false
    private var gotKey = false

    fun setSurface(s: Surface?) = synchronized(this) { surface = s; if (s == null) releaseDecoder() }
    private fun releaseDecoder() { runCatching { dec?.stop(); dec?.release() }; dec = null; gotKey = false }

    fun start(host: String, code: String) {
        stop(); run = true; active = true; status = "Waiting for the TV. Accept the prompts on the TV."
        thread(name = "tv-viewer") {
            while (run) {
                val closed = CountDownLatch(1); var opened = false
                val c = object : WebSocketClient(URI("ws://$host:8081/?code=$code")) {
                    override fun onOpen(h: ServerHandshake?) { opened = true; status = "Showing TV" }
                    override fun onMessage(m: String?) {}
                    override fun onMessage(b: ByteBuffer) { frame(b) }
                    override fun onClose(c: Int, r: String?, remote: Boolean) { closed.countDown() }
                    override fun onError(e: Exception?) { closed.countDown() }
                }
                ws = c; c.connect(); closed.await()
                if (!run) return@thread
                if (opened) { status = "The TV stopped sharing"; break }
                Thread.sleep(1500)                                   // TV not ready yet (still on the Allow prompt): try again
            }
            stop(keepStatus = true)
        }
    }

    fun stop(keepStatus: Boolean = false) {
        run = false; active = false
        runCatching { ws?.close() }; ws = null
        synchronized(this) { releaseDecoder() }
        runCatching { track?.release() }; track = null
        if (!keepStatus) status = "Not started"
    }

    private fun frame(b: ByteBuffer) {
        val type = b.get().toInt(); val ts = b.double
        val p = ByteArray(b.remaining()); b.get(p)
        if (type == 3) { audio(p); return }
        synchronized(this) {
            val s = surface ?: return
            if (type == 1 && dec == null) {                          // start decoding at the first full picture
                val f = MediaFormat.createVideoFormat("video/avc", 1280, 720).apply {
                    setInteger(MediaFormat.KEY_MAX_WIDTH, 1920); setInteger(MediaFormat.KEY_MAX_HEIGHT, 1080) }
                val d = MediaCodec.createDecoderByType("video/avc"); d.configure(f, s, null, 0); d.start(); dec = d
                thread(name = "tv-viewer-out") {
                    val info = MediaCodec.BufferInfo()
                    while (run && dec === d) { val o = runCatching { d.dequeueOutputBuffer(info, 10_000) }.getOrDefault(-1)
                        if (o >= 0) runCatching { d.releaseOutputBuffer(o, true) } }                // true = draw now, lowest delay
                }
            }
            if (type == 1) gotKey = true
            val d = dec ?: return
            if (!gotKey) return
            val i = d.dequeueInputBuffer(5000); if (i < 0) return
            d.getInputBuffer(i)!!.apply { clear(); put(p) }
            d.queueInputBuffer(i, 0, p.size, ts.toLong(), 0)
        }
    }

    private fun audio(p: ByteArray) {
        val t = track ?: AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT) * 2)
            .setTransferMode(AudioTrack.MODE_STREAM).build().also { it.play(); track = it }
        t.write(p, 0, p.size)
    }
}
