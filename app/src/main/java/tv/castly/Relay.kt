package tv.castly

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** Tells the relay "the TV showing this code should open my address". Only the phone's home-network address is sent. */
object Relay {
    const val BASE = "https://castly.tv"            // change to wherever you host /relay

    fun join(code: String, phoneUrl: String) = thread {
        Thread.sleep(1500)                           // let the phone's stream server finish starting
        runCatching {
            (URL("$BASE/join").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true; connectTimeout = 4000
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(JSONObject(mapOf("code" to code, "url" to phoneUrl)).toString().toByteArray()) }
            }.inputStream.close()
        }
    }
}
