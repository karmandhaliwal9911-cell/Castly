package tv.castly

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** Settings for the glass look. [state] is the colourful background that glass panels blur. */
data class GlassSpec(val state: HazeState, val on: Boolean, val blur: Dp, val tint: Color)
val LocalGlass = compositionLocalOf<GlassSpec?> { null }

/** A glass panel: blurs what is behind it (Android 12+), adds a light tint and a thin bright edge. On other themes it is a plain card. */
@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(16.dp)): Modifier {
    val g = LocalGlass.current
    return if (g == null || !g.on) this.background(MaterialTheme.colorScheme.surfaceVariant, shape)
    else this.clip(shape).hazeEffect(g.state) { blurRadius = g.blur; tints = listOf(HazeTint(g.tint)); noiseFactor = 0.03f }
        .border(1.dp, Color.White.copy(alpha = .22f), shape)
}

/** Soft coloured light behind everything, so the blur has something to bend. */
@Composable
fun Blobs(ac: Color, m: Modifier) = Canvas(m) {
    fun blob(c: Color, x: Float, y: Float, r: Float) {
        val o = Offset(x * size.width, y * size.height); val rad = r * size.width
        drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), o, rad), rad, o)
    }
    blob(ac.copy(alpha = .55f), .15f, .12f, .7f)
    blob(Color(0xFF7C3AED).copy(alpha = .5f), .95f, .38f, .6f)
    blob(Color(0xFF2563EB).copy(alpha = .5f), .2f, .95f, .7f)
}
