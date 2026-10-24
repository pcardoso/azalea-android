package pt.grok.sway.ui.exercises

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val LIFETIME = 4.5f

private val ConfettiColors = listOf(
    Color(0xFFFF2D55), Color(0xFFFF9500), Color(0xFFFFCC00), Color(0xFF34C759),
    Color(0xFF00C7BE), Color(0xFF007AFF), Color(0xFFAF52DE),
)

private class Piece {
    val x = Random.nextFloat()
    val delay = Random.nextFloat() * 0.8f
    val speed = 80f + Random.nextFloat() * 140f
    val sway = 10f + Random.nextFloat() * 30f
    val swayRate = 2f + Random.nextFloat() * 3f
    val phase = Random.nextFloat() * 6.28f
    val spin = (Random.nextFloat() - 0.5f) * 12f
    val flip = 3f + Random.nextFloat() * 6f
    val width = 6f + Random.nextFloat() * 5f
    val height = 9f + Random.nextFloat() * 6f
    val round = Random.nextFloat() < 0.25f
    val color = ConfettiColors.random()
}

/** One-shot confetti shower: pieces fall, sway and spin, then fade out after a few seconds. */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Respect the system "remove animations" setting.
    val animationsOff = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    if (animationsOff) return

    val pieces = remember { List(140) { Piece() } }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (time < LIFETIME) {
            time = (withFrameNanos { it } - start) / 1_000_000_000f
        }
    }
    if (time >= LIFETIME) return

    Canvas(modifier) {
        val density = density
        val opacity = (LIFETIME - time).coerceIn(0f, 1f)
        for (p in pieces) {
            val t = time - p.delay
            if (t <= 0f) continue
            val y = (-20f + p.speed * t + 120f * t * t) * density
            if (y > size.height + 20 * density) continue
            val x = p.x * size.width + sin(t * p.swayRate + p.phase) * p.sway * density
            translate(x, y) {
                rotate(degrees = t * p.spin * 57.3f, pivot = Offset.Zero) {
                    // Squash on one axis to fake the paper flipping in 3D.
                    scale(1f, abs(cos(t * p.flip)) * 0.8f + 0.2f, pivot = Offset.Zero) {
                        val pieceSize = Size(p.width * density, p.height * density)
                        val topLeft = Offset(-pieceSize.width / 2, -pieceSize.height / 2)
                        if (p.round) drawOval(p.color, topLeft, pieceSize, alpha = opacity)
                        else drawRect(p.color, topLeft, pieceSize, alpha = opacity)
                    }
                }
            }
        }
    }
}
