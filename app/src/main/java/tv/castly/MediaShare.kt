package tv.castly

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.*

data class MediaItem(val uri: Uri, val name: String, val type: String, val mime: String, val size: Long = 0)

/** What is being shared to the TV right now. The TV page asks for this every half second. */
object Share {
    val items = mutableStateListOf<MediaItem>()          // what you picked
    @Volatile var queue: List<MediaItem> = emptyList()   // snapshot given to the TV
    @Volatile var code = ""
    var active by mutableStateOf(false)
    var index by mutableIntStateOf(0)
    var playing by mutableStateOf(true)
    var pos by mutableDoubleStateOf(0.0)
    var dur by mutableDoubleStateOf(0.0)
    @Volatile var seek: Double? = null
    @Volatile var seq = 0

    fun go(i: Int) { index = i; pos = 0.0; dur = 0.0; seek = null; seq++; playing = true }
    fun next() { if (index < queue.size - 1) go(index + 1) }
    fun prev() { if (index > 0) go(index - 1) }
    fun seekTo(sec: Double) { seek = sec; seq++ }

    fun add(ctx: Context, uris: List<Uri>, type: String, persist: Boolean = false) {
        for (u in uris) {
            if (persist) runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            var n = "file"
            ctx.contentResolver.query(u, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0 && c.moveToFirst()) n = c.getString(i) }
            items.add(MediaItem(u, n, type, ctx.contentResolver.getType(u) ?: when (type) { "video" -> "video/mp4"; "audio" -> "audio/mpeg"; else -> "image/jpeg" }))
        }
    }
}

/** Files (APKs and others) being sent to the TV for download. */
object Send {
    val items = mutableStateListOf<MediaItem>()
    @Volatile var queue: List<MediaItem> = emptyList()
    var active by mutableStateOf(false)

    fun add(ctx: Context, uris: List<Uri>) {
        for (u in uris) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            var n = "file"; var sz = 0L
            ctx.contentResolver.query(u, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); val z = c.getColumnIndex(OpenableColumns.SIZE)
                if (c.moveToFirst()) { if (i >= 0) n = c.getString(i); if (z >= 0) sz = c.getLong(z) } }
            items.add(MediaItem(u, n, "file", ctx.contentResolver.getType(u) ?: "application/octet-stream", sz))
        }
    }
    fun addFile(f: java.io.File, name: String) { items.add(MediaItem(Uri.fromFile(f), name, "file", "application/vnd.android.package-archive", f.length())) }
}
