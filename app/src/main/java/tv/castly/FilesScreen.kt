package tv.castly

import android.content.pm.ApplicationInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun FilesPage(s: CastState, onSend: () -> Unit, onStop: () -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { Send.add(ctx, it) }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())) {
        Back("Send files", back)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Send APKs and other files to the TV. Keep your phone and TV on the same Wi\u2011Fi.", color = dim)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton({ pick.launch(arrayOf("*/*")) }, Modifier.weight(1f)) { Text("Choose files") }
                OutlinedButton({ apps = true }, Modifier.weight(1f)) { Text("Choose an app") }
            }
            Send.items.forEachIndexed { i, m ->
                ListItem({ Text(m.name, maxLines = 1) }, supportingContent = { Text("%.1f MB".format(m.size / 1048576.0), color = dim) },
                    trailingContent = { TextButton({ Send.items.removeAt(i) }) { Text("Remove") } }, colors = ListItemDefaults.colors(Color.Transparent))
            }
            if (s.tv == null) Text("Connect to a TV first.", color = dim)
            if (!Send.active) Button({ Send.queue = Send.items.toList(); Send.active = true; onSend() }, Modifier.fillMaxWidth(), enabled = Send.items.isNotEmpty() && s.tv != null) { Text("Send to TV") }
            else {
                Text("Ready. On the TV, press OK on each file to download it. APKs offer to install.", color = MaterialTheme.colorScheme.primary)
                OutlinedButton({ onStop() }, Modifier.fillMaxWidth()) { Text("Stop sending") }
            }
        }
    }
    if (apps) {
        val pm = ctx.packageManager
        val list = remember { pm.getInstalledApplications(0).filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }.sortedBy { pm.getApplicationLabel(it).toString().lowercase() } }
        AlertDialog({ apps = false }, confirmButton = { TextButton({ apps = false }) { Text("Close") } }, title = { Text("Choose an app") }, text = {
            LazyColumn { items(list) { a ->
                val label = pm.getApplicationLabel(a).toString()
                ListItem({ Text(label) }, Modifier.clickable { Send.addFile(File(a.sourceDir), "$label.apk"); apps = false },
                    supportingContent = { if (!a.splitSourceDirs.isNullOrEmpty()) Text("Split app: may not install on the TV") },
                    colors = ListItemDefaults.colors(Color.Transparent)) } }
        })
    }
}
