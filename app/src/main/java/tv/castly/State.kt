package tv.castly

import android.content.Context
import androidx.compose.runtime.*

class CastState {
    var tv by mutableStateOf<TvFinder.Tv?>(null)      // chosen TV (host "" = TV opens the website with the code)
    var relayCode by mutableStateOf<String?>(null)    // code the TV website is showing
    val btFound = mutableStateListOf<TvFinder.Tv>()   // TVs found over Bluetooth
    var btScanning by mutableStateOf(false)
    var live by mutableStateOf(false)
    val found = mutableStateListOf<TvFinder.Tv>()     // TVs with the Castly app, found on Wi-Fi
    var code by mutableStateOf(newCode())
    companion object { fun newCode() = (100000..999999).random().toString() }
}

/** Settings, saved on the phone. No account, no ads. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("castly", 0)
    val side = mutableIntStateOf(sp.getInt("side", 1080))     // 720 / 1080 / 2160 (short side)
    val fps = mutableIntStateOf(sp.getInt("fps", 60))
    val blur = mutableIntStateOf(sp.getInt("blur", 24))           // glass blur 0..40 (dp)
    val clear = mutableFloatStateOf(sp.getFloat("clear", 0.10f))  // glass tint, lower = more see-through
    val speed = mutableIntStateOf(sp.getInt("speed", 6))      // remote pointer speed 1..10
    val mbps = mutableIntStateOf(sp.getInt("mbps", 12))
    val audio = mutableStateOf(sp.getBoolean("audio", true))
    val theme = mutableStateOf(sp.getString("theme", "glass")!!)   // dark / black / glass
    val accent = mutableLongStateOf(sp.getLong("accent", 0xFF5EEAD4))
    val brand = mutableStateOf(sp.getString("brand", "android")!!)    // android / samsung / lg (Settings > TV remote)
    val tvIp = mutableStateOf(sp.getString("tvip", "")!!)
    fun getKey(k: String): String? = sp.getString(k, null)             // saved Samsung token / LG key
    fun putKey(k: String, v: String) = sp.edit().putString(k, v).apply()
    fun save() = sp.edit().putInt("side", side.intValue).putInt("fps", fps.intValue).putInt("mbps", mbps.intValue).putInt("speed", speed.intValue).putInt("blur", blur.intValue).putFloat("clear", clear.floatValue).putString("brand", brand.value).putString("tvip", tvIp.value)
        .putInt("speed", speed.intValue).putBoolean("audio", audio.value).putString("theme", theme.value).putLong("accent", accent.longValue).apply()
}
