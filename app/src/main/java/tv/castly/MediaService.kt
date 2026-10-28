package tv.castly

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.ParcelFileDescriptor
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject

/** Lets the TV play the picked files straight from the phone (port 8082). The screen is not mirrored; the phone screen can turn off. */
class MediaService : Service() {
    private var server: NanoHTTPD? = null
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("media", "Sharing to TV", NotificationManager.IMPORTANCE_LOW))
        startForeground(2, Notification.Builder(this, "media").setContentTitle("Sharing to TV").setSmallIcon(android.R.drawable.ic_media_play).build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        if (server == null) server = Srv().also { it.start() }
        return START_NOT_STICKY
    }
    override fun onDestroy() { server?.stop(); super.onDestroy() }

    private inner class Srv : NanoHTTPD(8082) {
        override fun serve(s: IHTTPSession): Response {
            val q = s.parameters
            if (q["code"]?.firstOrNull() != Share.code) return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "no")   // only the TV you allowed
            return when {
                s.uri == "/" -> { val b = assets.open(if (Send.active) "send.html" else "media.html").readBytes(); newFixedLengthResponse(Response.Status.OK, "text/html", b.inputStream(), b.size.toLong()) }
                s.uri == "/queue" -> json(JSONArray(Share.queue.mapIndexed { i, m -> JSONObject().put("name", m.name).put("type", m.type).put("url", "/f/$i") }).toString())
                s.uri == "/state" -> json(JSONObject().put("i", Share.index).put("playing", Share.playing).put("seek", Share.seek ?: JSONObject.NULL).put("seq", Share.seq).toString())
                s.uri == "/pos" -> { Share.pos = q["p"]?.firstOrNull()?.toDoubleOrNull() ?: Share.pos; Share.dur = q["d"]?.firstOrNull()?.toDoubleOrNull() ?: Share.dur; json("{}") }
                s.uri == "/ended" -> { Share.next(); json("{}") }
                s.uri == "/files" -> json(JSONArray(Send.queue.mapIndexed { i, m -> JSONObject().put("name", m.name).put("size", m.size).put("url", "/d/$i") }).toString())
                s.uri.startsWith("/d/") -> file(Send.queue.getOrNull(s.uri.removePrefix("/d/").toIntOrNull() ?: -1), s, true)
                s.uri.startsWith("/f/") -> file(Share.queue.getOrNull(s.uri.removePrefix("/f/").toIntOrNull() ?: -1), s)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "")
            }
        }
        private fun json(t: String) = newFixedLengthResponse(Response.Status.OK, "application/json", t)

        private fun file(m: MediaItem?, s: IHTTPSession, dl: Boolean = false): Response {          // supports seeking (Range requests)
            if (m == null) return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "")
            val pfd = contentResolver.openFileDescriptor(m.uri, "r") ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "")
            val len = pfd.statSize
            val r = s.headers["range"]; var a = 0L; var b = len - 1
            if (r != null && r.startsWith("bytes=")) { val p = r.substring(6).split("-"); a = p[0].toLongOrNull() ?: 0L; b = p.getOrNull(1)?.toLongOrNull() ?: (len - 1) }
            val ins = ParcelFileDescriptor.AutoCloseInputStream(pfd).also { it.channel.position(a) }
            val res = newFixedLengthResponse(if (r != null) Response.Status.PARTIAL_CONTENT else Response.Status.OK, m.mime, ins, b - a + 1)
            res.addHeader("Accept-Ranges", "bytes")
            if (dl) res.addHeader("Content-Disposition", "attachment; filename=\"${m.name.replace("\"", "")}\"")
            if (r != null) res.addHeader("Content-Range", "bytes $a-$b/$len")
            return res
        }
    }
}
