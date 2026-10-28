# Castly – step 1: screen + sound mirroring to a TV browser

## How it works
Phone: MediaProjection → H.264 hardware encoder (low-latency) + system audio capture → WebSocket (:8081).
TV: opens http://PHONE_IP:8080 → types the 6-digit code → WebCodecs decodes video to a canvas, Web Audio plays sound. Nothing installed on the TV.

## Setup
build.gradle (app) — minSdk 29, compileSdk 34:
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.java-websocket:Java-WebSocket:1.5.6")

AndroidManifest.xml:
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.RECORD_AUDIO"/>
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"/>
    <application android:usesCleartextTraffic="true" ...>
      <service android:name=".MirrorService" android:foregroundServiceType="mediaProjection" android:exported="false"/>

Starting a session (from your Start mirror button), after RECORD_AUDIO is granted:
    val mpm = getSystemService(MediaProjectionManager::class.java)
    launcher.launch(mpm.createScreenCaptureIntent())     // Android 14+ lets the user pick "single app"
    // in the result callback:
    startForegroundService(Intent(this, MirrorService::class.java)
        .putExtra("result", res.resultCode).putExtra("data", res.data)
        .putExtra("code", code).putExtra("fps", 60).putExtra("mbps", 12).putExtra("short_side", 1080))
Show MirrorService.phoneIp() + ":8080" and the code on the Connect screen.

## Notes
- Expect roughly 100–250 ms delay on good Wi‑Fi. Lower bitrate / 720p / 30 fps cut it further.
- The TV browser must support WebCodecs (Chrome 94+, most recent Android TV / Google TV browsers).
- Next steps: Compose UI from the prototype, TV discovery, remote, file transfer.

## Step 2: Castly TV app (Android TV, Google TV, Fire TV)
tv-app/ is a small TV app (APK). It advertises the TV on Wi-Fi (NSD "_castly._tcp", port 8090). The phone's TvFinder lists it;
tapping it calls /pair on the TV, the TV asks "Allow?", then its WebView opens http://PHONE_IP:8080/?code=... and plays the stream.
- tv-app build.gradle: minSdk 21, `implementation("org.nanohttpd:nanohttpd:2.3.1")`. Add a 320x180 `drawable/banner` image.
- Needs a WebView with WebCodecs (Chrome/WebView 94+). Update "Android System WebView" on older TVs.
- Samsung (Tizen) and LG (webOS): same web page wrapped as a TV web app; they cannot advertise on Wi-Fi, so they use the code. Roku cannot run this stream.

## Step 3: phone app screens (Jetpack Compose)
Files: State.kt, MainActivity.kt, Ui.kt (+ TvFinder.kt, MirrorService.kt, MirrorServer.kt).
Gradle: compose BOM 2024.09+, `androidx.activity:activity-compose`, `androidx.compose.material3:material3`,
`androidx.compose.material:material-icons-extended`. Manifest: add `<activity android:name=".MainActivity" android:exported="true">` with the MAIN/LAUNCHER filter.
Done: side panel, Home + Start mirror sheet (whole screen / one app on Android 14+), Connect (Wi-Fi list + Code), Settings (video, sound, Dark/Black/Glass, accent).
Next: Share media, Mirror TV to phone, Remote, Files screens; Bluetooth/USB connect; real blur for the glass look.

## Step 4: TV remote (Buttons / Touchpad / Keyboard)
Phone: RemoteClient.kt + RemoteScreen.kt. TV: RemoteService.kt (an Accessibility service) + routes in TvActivity.
- One-time on the TV: Settings > Accessibility > Castly Remote > On. Add `<string name="a11y_desc">Lets your phone control this TV</string>` to tv-app strings.xml.
- Only the phone you tapped "Allow" for can send commands. Pointer moves use UDP :8091, taps/typing use HTTP :8090.
- Pointer speed (1-10) is saved in Prefs; default 6 is about 2x faster than before.
- Limits: it cannot press the TV's power button, and arrow keys move focus in apps that support it (most TV apps do). Some apps block typing from accessibility services.

## Step 4b: scroll strip + pointer speed
Touchpad screen has a separate scroll strip (like a mouse wheel) and a Pointer speed slider (1-10, saved in Prefs.speed).
Phone sends "s <dy>" over UDP :8091; RemoteService.scroll() turns it into swipes at the pointer. Keyboard sends the whole text to the TV field (HTTP /text).

## Step 5: QR code connect
TV app shows a QR (castly://pair?h=TV_IP&p=8090&n=NAME) on its start screen. Phone: Connect to TV > Other > Scan QR code (Google code scanner) picks that TV; Start mirror then asks the TV "Allow?" as usual.
Gradle: phone `com.google.android.gms:play-services-code-scanner:16.1.0`; TV app `com.google.zxing:core:3.5.3`.
Not done: a QR on the castly.tv website page (needs a small relay server so the page can be reached before the phone is known).

## Step 6: castly.tv relay (TV website without typing the phone address)
relay/ is a small Node server. TV opens the site -> code + QR -> phone (Connect > Code, or Other > Scan QR) -> TV jumps to the phone and streams directly over Wi-Fi.
Phone: Relay.kt (set Relay.BASE to your host). Fallback with no internet: open PHONE_IP:8080 on the TV and type the code shown on the phone.

## Step 7: Samsung and LG remote (Settings > TV remote)
Side panel and Remote screen are unchanged. Settings > TV remote: choose Android TV / Samsung / LG, type the TV's IP address, press Pair with TV, accept the prompt on the TV. Token/key is saved.
Files: Remote.kt (interface), SamsungRemote.kt (Tizen, wss :8002), LgRemote.kt (webOS, wss :3001 or ws :3000), RemoteClient.kt (Castly TV app).
Samsung: scroll is stepped (no wheel); click = Enter. LG: real pointer, wheel, typing (a text box must be open on the TV). Samsung: TV Settings > General > External Device Manager > Device Connection Manager > Access Notification.
LG: turn on "LG Connect Apps" / "Mobile TV On". Mirroring on these TVs still uses the TV browser + code (castly.tv).

## Step 8: share video, music, photos (no mirroring)
Phone: MediaScreen.kt (pick files, Play on TV, Prev/Pause/Next/seek/Stop), MediaShare.kt, MediaService.kt (NanoHTTPD :8082 with seek support). TV page: assets/media.html (works in the TV app and in a TV browser via the relay).
Manifest: `<service android:name=".MediaService" android:foregroundServiceType="mediaPlayback" android:exported="false"/>` + permission FOREGROUND_SERVICE_MEDIA_PLAYBACK.
The TV plays files with its own browser/WebView, so it supports what that supports (MP4/H.264, MP3/AAC, JPEG/PNG; MKV and some audio formats may not play). Pause/seek react within about half a second.

## Step 9: send APKs and files (Send files)
Phone: FilesScreen.kt (Choose files / Choose an app, Send to TV, Stop), Send object in MediaShare.kt, routes /files and /d/N in MediaService.kt, assets/send.html (TV page with big download buttons).
TV app: DownloadListener + DownloadManager saves to its Download folder; APKs open Android's installer (FileProvider + REQUEST_INSTALL_PACKAGES, provider and file_paths.xml added). First install asks the TV to allow "install unknown apps" for Castly.
tv-app Gradle: `implementation("androidx.core:core-ktx:1.13.1")`. Phone: "Choose an app" needs QUERY_ALL_PACKAGES in the manifest (fine for sideloading; Google Play restricts it).
Limits: apps that come as several parts (split APKs / app bundles) may not install from one file. Many TVs block installs unless "unknown sources" is on. In a TV browser (relay) the browser downloads the file; installing is then up to the TV's file manager.

## Step 10: mirror the TV to the phone
Phone: TvToPhoneScreen.kt (preview, tap for full screen), TvView.kt (connects to the TV on port 8081, decodes H.264 with MediaCodec, plays sound with AudioTrack), TvFinder.mirrorRequest/mirrorStop.
TV app: reuses MirrorService + MirrorServer (copied into tv-app) with /mirror and /mirrorstop routes in TvActivity: "Allow?" dialog -> RECORD_AUDIO -> Android screen-capture prompt -> stream at 720p / 30 fps.
Only works on Android TV / Google TV devices that include the screen-capture prompt (many Fire TV and some TVs do not). Protected video (Netflix etc.) shows black. Samsung/LG cannot do this.

## Step 11: Bluetooth find (USB not supported)
TV app: BleBeacon.kt advertises a BLE service and shares {name, Wi-Fi address, port} when a phone reads it. Phone: BleFinder.kt scans 12 s; Connect to TV > Other > Find TVs with Bluetooth.
Phone manifest: BLUETOOTH_SCAN (usesPermissionFlags neverForLocation), BLUETOOTH_CONNECT, ACCESS_FINE_LOCATION (maxSdk 30), `<uses-feature android:name="android.hardware.bluetooth_le" android:required="false"/>`.
Bluetooth only finds the TV; the phone and TV must still be on the same Wi-Fi. Some TV boxes cannot advertise over Bluetooth and then will not appear.
USB: a USB cable cannot carry video to a normal TV (the TV port acts as a host, not a screen input), so Wi-Fi is the supported way.

## Step 12: Samsung and LG TV apps
tv-samsung/ (Tizen) and tv-lg/ (webOS): small apps that open the relay, show the code and QR, then jump to the phone's stream. No phone changes: use Connect to TV > Code or Other > Scan QR.
Mirroring needs WebCodecs: Samsung 2022+ (Tizen 7), LG 2023+ (webOS 23). Older TVs: browser-based sharing and file download still work. These two TV brands cannot be found in the Wi-Fi list.
Needs developer certificates/accounts and the vendors' tools (Tizen Studio, webOS CLI) to build and install. See each folder's README.

## Step 13: real glass blur
Glass.kt: Modifier.glass() blurs the coloured background behind panels (Haze library), plus soft light blobs so the blur is visible. Used on the side panel, Home button and the Start mirror choices; Settings > Look has Glass blur and Glass tint sliders.
Gradle: `implementation("dev.chrisbanes.haze:haze:1.0.0")` (check the latest version; function names changed between versions).
Real blur needs Android 12+; older phones show the see-through tint without blur. The blur sees the background light only, not other panels. Panels inside bottom sheets get the tint only (sheets are separate windows).
