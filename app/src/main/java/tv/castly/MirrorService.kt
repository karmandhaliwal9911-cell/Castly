package tv.castly

import android.app.*
import android.content.*
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.util.DisplayMetrics
import java.net.NetworkInterface
import kotlin.concurrent.thread

class MirrorService : Service() {
    private var proj: MediaProjection? = null
    private var enc: MediaCodec? = null
    private var server: MirrorServer? = null
    @Volatile private var running = false

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == "stop") { stopSelf(); return START_NOT_STICKY }
        val ch = "mirror"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(ch, "Mirroring", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, Notification.Builder(this, ch).setContentTitle("Mirroring to TV")
            .setSmallIcon(android.R.drawable.ic_media_play).build(),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)

        val code = i!!.getStringExtra("code")!!
        val fps = i.getIntExtra("fps", 60)
        val mbps = i.getIntExtra("mbps", 12)
        val shortSide = i.getIntExtra("short_side", 1080)       // 720 / 1080 / 2160
        val audio = i.getBooleanExtra("audio", true)

        val mpm = getSystemService(MediaProjectionManager::class.java)
        proj = mpm.getMediaProjection(i.getIntExtra("result", 0), i.getParcelableExtra("data")!!)
        proj!!.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, null)

        server = MirrorServer(this, code) { }.also { it.start() }
        startVideo(fps, mbps, shortSide)
        if (audio) startAudio()
        return START_NOT_STICKY
    }

    private fun startVideo(fps: Int, mbps: Int, shortSide: Int) {
        val dm = resources.displayMetrics
        val scale = shortSide.toFloat() / minOf(dm.widthPixels, dm.heightPixels)
        val w = (dm.widthPixels * scale).toInt() and 1.inv()
        val h = (dm.heightPixels * scale).toInt() and 1.inv()
        val fmt = MediaFormat.createVideoFormat("video/avc", w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, mbps * 1_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            setInteger(MediaFormat.KEY_LATENCY, 1)                 // low-latency encode
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000)
        }
        val e = MediaCodec.createEncoderByType("video/avc").also { enc = it }
        e.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = e.createInputSurface()
        e.start()
        proj!!.createVirtualDisplay("castly", w, h, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, null)
        running = true
        thread(name = "video-out") {
            val info = MediaCodec.BufferInfo(); var config: ByteArray? = null
            while (running) {
                val idx = try { e.dequeueOutputBuffer(info, 10_000) } catch (x: Exception) { break }
                if (idx < 0) continue
                val buf = e.getOutputBuffer(idx)!!
                val data = ByteArray(info.size).also { buf.position(info.offset); buf.get(it) }
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) config = data
                else {
                    val key = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                    server?.send(if (key) 1 else 2, System.nanoTime() / 1000 - 15_000,   // same clock as audio if (key && config != null) config + data else data)
                }
                e.releaseOutputBuffer(idx, false)
            }
        }
    }

    private fun startAudio() {   // phone sound -> TV (Android 10+, needs RECORD_AUDIO)
        val cfg = AudioPlaybackCaptureConfiguration.Builder(proj!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).build()
        val af = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build()
        val rec = AudioRecord.Builder().setAudioPlaybackCaptureConfig(cfg).setAudioFormat(af)
            .setBufferSizeInBytes(3840 * 4).build()
        rec.startRecording()
        thread(name = "audio-out") {
            val b = ByteArray(3840)                                // 20 ms of stereo PCM
            while (running) {
                val n = rec.read(b, 0, b.size)
                if (n > 0) server?.send(3, System.nanoTime() / 1000 - 20_000, b.copyOf(n))   // capture time = end of read minus 20 ms
            }
            rec.release()
        }
    }

    override fun onDestroy() {
        running = false
        runCatching { enc?.stop(); enc?.release() }
        server?.stop(); proj?.stop()
    }

    companion object {
        fun phoneIp(): String = NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }?.hostAddress ?: "?"
    }
}
