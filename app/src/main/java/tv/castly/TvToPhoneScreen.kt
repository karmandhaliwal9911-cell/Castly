package tv.castly

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun TvToPhonePage(s: CastState, onStart: () -> Unit, onStop: () -> Unit, back: () -> Unit) {
    var full by remember { mutableStateOf(false) }
    val live = TvView.status == "Showing TV"
    BackHandler(full) { full = false }
    val video: @Composable (Modifier) -> Unit = { m ->
        AndroidView({ c -> SurfaceView(c).apply { holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) { TvView.setSurface(h.surface) }
            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}
            override fun surfaceDestroyed(h: SurfaceHolder) { TvView.setSurface(null) } }) } }, m)
    }
    if (full) { Box(Modifier.fillMaxSize().background(Color.Black).clickable { full = false }) { video(Modifier.fillMaxSize()) }; return }   // tap or Back to leave
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Back("Mirror TV to phone", back)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Watch what is on your TV screen on this phone. Tap the picture for full screen.", color = dim)
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.large).background(Color.Black).clickable(enabled = live) { full = true }, Alignment.Center) {
                if (TvView.active) video(Modifier.fillMaxSize())
                if (!live) Text(TvView.status, color = Color.White.copy(alpha = .7f), modifier = Modifier.padding(16.dp))
            }
            if (s.tv?.host.isNullOrEmpty()) Text("This needs the Castly TV app. Pick your TV from the Wi\u2011Fi list in Connect to TV.", color = dim)
            if (!TvView.active) Button(onStart, Modifier.fillMaxWidth(), enabled = !s.tv?.host.isNullOrEmpty()) { Text("Show TV on phone") }
            else OutlinedButton(onStop, Modifier.fillMaxWidth()) { Text("Stop") }
            Text("The TV asks you to allow it, then Android asks to capture the screen. Some TVs and apps (for example Netflix) show a black picture.", color = dim, style = MaterialTheme.typography.bodySmall)
        }
    }
}
