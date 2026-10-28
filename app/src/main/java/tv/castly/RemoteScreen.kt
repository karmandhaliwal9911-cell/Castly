package tv.castly

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun RemotePage(s: CastState, p: Prefs, rc: RemoteClient, back: () -> Unit) {
    var tab by remember { mutableIntStateOf(1) }
    val host = s.tv?.host.orEmpty()
    val brand = p.brand.value
    LaunchedEffect(host) { if (host.isNotEmpty()) rc.connect(host) }
    val r: Remote = remember(brand, p.tvIp.value) { if (brand == "android") rc else makeRemote(p)!! }   // Samsung / LG chosen in Settings
    DisposableEffect(r) { onDispose { if (r !== rc) r.close() } }
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())) {
        Back("TV remote", back)
        if (if (brand == "android") host.isEmpty() else p.tvIp.value.isBlank()) {
            Text(when { brand != "android" -> "Add your ${if (brand == "lg") "LG" else "Samsung"} TV's IP address in Settings > TV remote."
                s.tv == null -> "Connect to a TV first."
                else -> "The remote needs the Castly TV app. Pick your TV from the Wi\u2011Fi list." },
                Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        if (brand == "android") Text("First time: on the TV turn on Settings > Accessibility > Castly Remote.", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TabRow(tab, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
            listOf("Buttons", "Touchpad", "Keyboard").forEachIndexed { i, t -> Tab(tab == i, { tab = i }) { Text(t, Modifier.padding(14.dp)) } }
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (tab) {
                0 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Key(r, "Left", "LEFT"); Key(r, "Up", "UP"); Key(r, "Right", "RIGHT") }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Key(r, "Down", "DOWN"); Key(r, "OK", "OK", true) }
                }
                1 -> Touchpad(p, r)
                else -> Typing(r)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Key(r, "Vol \u2212", "VOL_DOWN"); Key(r, "Vol +", "VOL_UP") }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Key(r, "Back", "BACK"); Key(r, "Enter", "ENTER", true); Key(r, "Home", "HOME") }
        }
    }
}

@Composable
private fun RowScope.Key(rc: Remote, label: String, key: String, main: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Surface(Modifier.weight(1f).height(56.dp).clickable { rc.key(key) }, RoundedCornerShape(16.dp),
        if (main) c.primary else c.surfaceVariant) {
        Box(contentAlignment = Alignment.Center) { Text(label, fontWeight = FontWeight.Bold, color = if (main) c.onPrimary else c.onSurface) }
    }
}

@Composable
private fun Touchpad(p: Prefs, rc: Remote) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(300.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(c.surfaceVariant)
            .pointerInput(Unit) { detectTapGestures(onTap = { rc.click() }) }
            .pointerInput(p.speed.intValue) {                       // slide = move pointer; speed comes from the slider
                detectDragGestures { ch, d -> ch.consume(); val f = p.speed.intValue * 0.6f; rc.move((d.x * f).toInt(), (d.y * f).toInt()) }
            }, Alignment.Center) { Text("Slide to move \u00b7 Tap to click", color = c.onSurfaceVariant) }
        Box(Modifier.width(56.dp).fillMaxHeight().clip(RoundedCornerShape(28.dp)).background(c.surfaceVariant)   // separate scroll strip, like a mouse wheel
            .pointerInput(Unit) { detectVerticalDragGestures { ch, dy -> ch.consume(); rc.scroll(dy.toInt()) } },
            Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxHeight().padding(12.dp)) {
                Text("\u25b2", color = c.onSurfaceVariant); Text("Scroll", color = c.primary, style = MaterialTheme.typography.labelSmall); Text("\u25bc", color = c.onSurfaceVariant) } }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Pointer speed", Modifier.weight(1f))
        Slider(p.speed.intValue.toFloat(), { p.speed.intValue = it.toInt() }, Modifier.weight(1.4f), valueRange = 1f..10f, onValueChangeFinished = { p.save() })
    }
}

@Composable
private fun Typing(rc: Remote) {
    var text by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    OutlinedTextField(text, { n -> text = n; rc.text(n) },                 // every change sends the whole text to the TV field
        Modifier.fillMaxWidth().focusRequester(fr), placeholder = { Text("Type here to write on the TV") })
    OutlinedButton({ text = "" }) { Text("Clear box") }
}
