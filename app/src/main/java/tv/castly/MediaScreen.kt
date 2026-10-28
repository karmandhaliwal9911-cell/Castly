package tv.castly

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun MediaPage(s: CastState, onShare: () -> Unit, onStop: () -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val visual = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { Share.add(ctx, it, if (tab == 0) "video" else "photo") }
    val audio = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { Share.add(ctx, it, "audio", persist = true) }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())) {
        Back("Share to TV", back)
        Text("Play one video, song or photo on the TV without mirroring your whole screen.", Modifier.padding(horizontal = 20.dp), color = dim)
        TabRow(tab, containerColor = Color.Transparent) { listOf("Video", "Music", "Photos").forEachIndexed { i, t -> Tab(tab == i, { tab = i }) { Text(t, Modifier.padding(14.dp)) } } }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton({ when (tab) {
                0 -> visual.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                1 -> audio.launch(arrayOf("audio/*"))
                else -> visual.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }, Modifier.fillMaxWidth()) { Text("Choose from phone") }
            Share.items.forEachIndexed { i, m ->
                ListItem({ Text(m.name, maxLines = 1) }, Modifier.clickable { if (Share.active) Share.go(i) },
                    supportingContent = { Text(m.type, color = dim) }, trailingContent = { TextButton({ Share.items.removeAt(i) }) { Text("Remove") } },
                    colors = ListItemDefaults.colors(Color.Transparent))
            }
            if (s.tv == null) Text("Connect to a TV first.", color = dim)
            Button({ Share.queue = Share.items.toList(); Share.go(0); Share.code = CastState.newCode(); Share.active = true; onShare() },
                Modifier.fillMaxWidth(), enabled = Share.items.isNotEmpty() && s.tv != null) { Text(if (Share.active) "Play again on TV" else "Play on TV") }
            if (Share.active) NowPlaying(onStop)
        }
    }
}

@Composable
private fun NowPlaying(onStop: () -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val frac = if (Share.dur > 0) (Share.pos / Share.dur).toFloat() else 0f
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(16.dp)) {
            Text(Share.queue.getOrNull(Share.index)?.name ?: "", maxLines = 1)
            Text("Playing on TV", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            Slider(drag ?: frac, { drag = it }, onValueChangeFinished = { drag?.let { Share.seekTo(it * Share.dur) }; drag = null }, enabled = Share.dur > 0)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                TextButton({ Share.prev() }) { Text("Prev") }
                Button({ Share.playing = !Share.playing }) { Text(if (Share.playing) "Pause" else "Play") }
                TextButton({ Share.next() }) { Text("Next") }
                TextButton({ onStop() }) { Text("Stop") }
            }
        }
    }
}
