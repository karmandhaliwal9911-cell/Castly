package tv.castly

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

@Composable
fun CastlyTheme(p: Prefs, content: @Composable () -> Unit) {
    val ac = Color(p.accent.longValue); val glass = p.theme.value == "glass"
    val base = when (p.theme.value) { "black" -> Color.Black; "glass" -> Color(0xFF070B16); else -> Color(0xFF0E1116) }
    val card = if (glass) Color.White.copy(alpha = .10f) else if (p.theme.value == "black") Color(0xFF0B0D11) else Color(0xFF171B22)
    MaterialTheme(colorScheme = darkColorScheme(primary = ac, onPrimary = Color(0xFF06201C), background = base,
        surface = base, surfaceVariant = card, onSurface = Color(0xFFEEF1F6), onSurfaceVariant = Color(0xFF8A94A6),
        surfaceContainerLow = if (glass) Color(0xFF121830) else card, surfaceContainerHigh = if (glass) Color(0xFF121830) else card)) {
        val hz = remember { HazeState() }
        CompositionLocalProvider(LocalGlass provides GlassSpec(hz, glass, p.blur.intValue.dp, Color.White.copy(alpha = p.clear.floatValue))) {
            Box(Modifier.fillMaxSize().background(base)) {
                if (glass) Blobs(ac, Modifier.fillMaxSize().hazeSource(hz))      // what the glass panels blur
                content()
            }
        }
    }
}

@Composable
fun CastlyApp(s: CastState, p: Prefs, onStart: (Boolean) -> Unit, onStop: () -> Unit, rc: RemoteClient, onScan: (() -> Unit) -> Unit, onShare: () -> Unit, onShareStop: () -> Unit, onSend: () -> Unit, onSendStop: () -> Unit, onTvMirror: () -> Unit, onTvMirrorStop: () -> Unit, onBle: () -> Unit) {
    val drawer = rememberDrawerState(DrawerValue.Closed); val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf("home") }
    fun go(x: String) { page = x; scope.launch { drawer.close() } }
    BackHandler(page != "home") { page = "home" }
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet(drawerContainerColor = Color.Transparent, modifier = Modifier.glass(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))) {
            Text("Castly", Modifier.padding(24.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            listOf("connect" to "Connect to TV", "media" to "Share video, music, photos", "tv2phone" to "Mirror TV to phone",
                "remote" to "TV remote", "files" to "Send files", "settings" to "Settings").forEach { (k, t) ->
                NavigationDrawerItem(label = { Text(t) }, selected = page == k, onClick = { go(k) }, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
    }) {
         when (page) {
            "home" -> Home(s, { scope.launch { drawer.open() } }, { go("connect") }, onStart, onStop)
            "connect" -> Connect(s, onScan, onBle) { go("home") }
            "remote" -> RemotePage(s, p, rc) { go("home") }
            "remote" -> RemotePage(s, p, rc) { go("home") }
            "tv2phone" -> TvToPhonePage(s, onTvMirror, onTvMirrorStop) { go("home") }
            "files" -> FilesPage(s, onSend, onSendStop) { go("home") }
            "media" -> MediaPage(s, onShare, onShareStop) { go("home") }
            "settings" -> SettingsPage(p) { go("home") }
            else -> Column(Modifier.fillMaxSize().systemBarsPadding()) { Back("Coming next") { go("home") } }
        }
    }
}

@Composable fun Back(title: String, onBack: () -> Unit) = Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
    IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(s: CastState, openMenu: () -> Unit, openConnect: () -> Unit, onStart: (Boolean) -> Unit, onStop: () -> Unit) {
    var sheet by remember { mutableStateOf(false) }
    val ac = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        IconButton(openMenu, Modifier.padding(4.dp)) { Icon(Icons.Default.Menu, "Open menu") }
        Column(Modifier.weight(1f).fillMaxWidth(), Arrangement.Center, Alignment.CenterHorizontally) {
            Box(Modifier.size(200.dp).clip(CircleShape).then(if (s.live) Modifier.background(ac) else Modifier.glass(CircleShape))
                .clickable { when { s.tv == null -> openConnect(); s.live -> onStop(); else -> sheet = true } }, Alignment.Center) {
                Icon(Icons.Default.Cast, null, Modifier.size(76.dp), tint = if (s.live) MaterialTheme.colorScheme.onPrimary else ac)
            }
            Spacer(Modifier.height(24.dp))
            Text(if (s.live) "Mirroring" else if (s.tv != null) "Start mirror" else "Not connected", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(if (s.live) "${s.tv?.name} · tap to stop" else if (s.tv != null) "${s.tv?.name} connected · tap to choose what to show" else "Tap to connect to a TV",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (sheet) ModalBottomSheet({ sheet = false }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("What do you want to show?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Choice("Whole screen", "Everything on your phone appears on the TV", true) { sheet = false; onStart(true) }
            Choice("Just an app", if (Build.VERSION.SDK_INT >= 34) "Pick the app in the next step" else "Needs Android 14 or newer", Build.VERSION.SDK_INT >= 34) { sheet = false; onStart(false) }
        }
    }
}

@Composable fun Choice(t: String, sub: String, on: Boolean, click: () -> Unit) =
    Surface(Modifier.padding(top = 12.dp).fillMaxWidth().glass(RoundedCornerShape(16.dp)).clickable(on, onClick = click), RoundedCornerShape(16.dp), Color.Transparent) {
        Column(Modifier.padding(16.dp)) { Text(t, fontWeight = FontWeight.Bold); Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }

@Composable
fun Connect(s: CastState, onScan: (() -> Unit) -> Unit, onBle: () -> Unit, done: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Back("Connect to TV", done)
        TabRow(tab, containerColor = Color.Transparent) {
            Tab(tab == 0, { tab = 0 }) { Text("Wi\u2011Fi", Modifier.padding(14.dp)) }
            Tab(tab == 1, { tab = 1 }) { Text("Code", Modifier.padding(14.dp)) }
            Tab(tab == 2, { tab = 2 }) { Text("Other", Modifier.padding(14.dp)) }
        }
        Column(Modifier.padding(20.dp)) {
            if (tab == 0) {
                Text("No code needed. Keep your phone and TV on the same Wi\u2011Fi.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s.found.isEmpty()) Text("Looking for TVs with the Castly app\u2026", Modifier.padding(top = 16.dp))
                LazyColumn { items(s.found) { tv -> ListItem({ Text(tv.name) }, Modifier.clickable { s.tv = tv; s.relayCode = null; done() }, colors = ListItemDefaults.colors(Color.Transparent)) } }
                Text("TV not listed? Use the Code tab.", Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (tab == 2) {
                Text("Scan the QR code on your TV", fontWeight = FontWeight.ExtraBold)
                Text("Open the Castly app on your TV. A QR code appears on the screen. No code to type.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button({ onScan(done) }, Modifier.padding(top = 16.dp).fillMaxWidth()) { Text("Scan QR code") }
                Text("Or find the TV with Bluetooth", Modifier.padding(top = 24.dp), fontWeight = FontWeight.ExtraBold)
                OutlinedButton(onBle, Modifier.padding(top = 8.dp).fillMaxWidth(), enabled = !s.btScanning) { Text(if (s.btScanning) "Looking\u2026" else "Find TVs with Bluetooth") }
                s.btFound.forEach { tv -> ListItem({ Text(tv.name) }, Modifier.clickable { s.tv = tv; s.relayCode = null; done() }, colors = ListItemDefaults.colors(Color.Transparent)) }
                Text("Bluetooth only finds the TV. Video and sound still go over Wi\u2011Fi. USB is not supported: most TVs cannot take video over a USB cable.",
                    Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("1. Open castly.tv on your TV", fontWeight = FontWeight.ExtraBold)
                Text("A code and a QR code appear on the TV.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("2. Type the code shown on the TV", Modifier.padding(top = 20.dp), fontWeight = FontWeight.ExtraBold)
                var typed by remember { mutableStateOf("") }
                OutlinedTextField(typed, { if (it.length <= 6 && it.all(Char::isDigit)) typed = it }, Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    singleLine = true, placeholder = { Text("6-digit code") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Button({ s.tv = TvFinder.Tv("TV browser", "", 0); s.relayCode = typed; done() }, Modifier.fillMaxWidth(), enabled = typed.length == 6) { Text("Connect") }
                Text("No internet? Open ${MirrorService.phoneIp()}:8080 in the TV browser and type ${s.code} there.", Modifier.padding(top = 20.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SettingsPage(p: Prefs, back: () -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())) {
        Back("Settings", back)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Video", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Chips(listOf(720 to "720p", 1080 to "1080p", 2160 to "4K"), p.side.intValue) { p.side.intValue = it; p.save() }
            Chips(listOf(30 to "30 fps", 60 to "60 fps"), p.fps.intValue) { p.fps.intValue = it; p.save() }
            Text("Bitrate: ${p.mbps.intValue} Mbps")
            Slider(p.mbps.intValue.toFloat(), { p.mbps.intValue = it.toInt() }, valueRange = 2f..40f, onValueChangeFinished = { p.save() })
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Phone sound on TV", Modifier.weight(1f)); Switch(p.audio.value, { p.audio.value = it; p.save() }) }
            Text("Look", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Chips(listOf("dark" to "Dark", "black" to "Black", "glass" to "Glass"), p.theme.value) { p.theme.value = it; p.save() }
            Chips(listOf(0xFF5EEAD4 to "Mint", 0xFF60A5FA to "Blue", 0xFFC084FC to "Purple", 0xFFF472B6 to "Pink"), p.accent.longValue) { p.accent.longValue = it; p.save() }
            if (p.theme.value == "glass") {
                Text("Glass blur: ${p.blur.intValue}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(p.blur.intValue.toFloat(), { p.blur.intValue = it.toInt() }, valueRange = 0f..40f, onValueChangeFinished = { p.save() })
                Text("Glass tint: ${(p.clear.floatValue * 100).toInt()}% (lower is more see-through)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(p.clear.floatValue, { p.clear.floatValue = it }, valueRange = 0.02f..0.30f, onValueChangeFinished = { p.save() })
            }
            Text("TV remote", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Chips(listOf("android" to "Android TV", "samsung" to "Samsung", "lg" to "LG"), p.brand.value) { p.brand.value = it; p.save() }
            if (p.brand.value != "android") {
                OutlinedTextField(p.tvIp.value, { p.tvIp.value = it.filter { c -> c.isDigit() || c == '.' }; p.save() }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("TV IP address (TV Settings > Network)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                var r by remember { mutableStateOf<Remote?>(null) }
                DisposableEffect(Unit) { onDispose { r?.close() } }
                Button({ r?.close(); r = makeRemote(p) }, enabled = p.tvIp.value.isNotBlank()) { Text("Pair with TV") }
                Text(r?.status?.value ?: "Press Pair, then accept the prompt on the TV.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable fun <T> Chips(items: List<Pair<T, String>>, sel: T, pick: (T) -> Unit) =
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items.forEach { (v, l) -> FilterChip(sel == v, { pick(v) }, { Text(l) }) } }
