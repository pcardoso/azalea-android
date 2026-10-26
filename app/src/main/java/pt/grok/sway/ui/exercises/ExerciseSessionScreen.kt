package pt.grok.sway.ui.exercises

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pt.grok.sway.R
import pt.grok.sway.data.ExerciseRun
import pt.grok.sway.data.ExerciseStep
import pt.grok.sway.data.TonePlayer
import pt.grok.sway.ui.LocalApp
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

private val HoldColors = listOf(Color(0xFFFF6B8B), Color(0xFFFF2D55))
private val RestColors = listOf(Color(0xFF63E6D4), Color(0xFF00C7BE))
/** SwiftUI's easeInOut curve, so both apps breathe the same way. */
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/** Degrees per second for the wavy rings; they turn one way while holding, the other while resting. */
private const val RING_SPEED = 12.0
/** Seconds for the spin to ease toward its new direction, like a flywheel reversing. */
private const val RING_INERTIA = 0.8

/** Guided hold/rest session: a circle grows while holding and shrinks while resting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseSessionScreen(nav: NavController) {
    val app = LocalApp.current
    val haptics = LocalHapticFeedback.current
    val prescription = app.session.state.value.prescription
    val steps = remember { prescription?.exerciseSequence.orEmpty() }
    val tones = remember { TonePlayer() }

    var index by remember { mutableIntStateOf(0) }
    var elapsedBeforePause by remember { mutableLongStateOf(0L) }
    var stepStartedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var paused by remember { mutableStateOf(false) }
    var pausedForDialog by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(steps.isEmpty()) }
    var confirmingExit by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf(false) }
    val startedAt = remember { System.currentTimeMillis() }
    // Ring rotation and spin banked from previous steps, so reversing direction doesn't jump.
    var ringBaseAngle by remember { mutableDoubleStateOf(0.0) }
    var ringStartVelocity by remember { mutableDoubleStateOf(RING_SPEED) }

    // Keep the screen on and release audio while the session is open.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            tones.release()
        }
    }

    /** Saves the run once, locally first, then sends it like a diary entry. */
    fun record(terminatedEarly: Boolean) {
        if (recorded || steps.isEmpty()) return
        recorded = true
        val run = ExerciseRun(
            prescriptionId = prescription?.prescription?.id,
            startedAt = startedAt,
            endedAt = System.currentTimeMillis(),
            completedSteps = if (terminatedEarly) index else steps.size,
            totalSteps = steps.size,
            terminatedEarly = terminatedEarly,
        )
        val token = app.session.state.value.submissionToken
        app.applicationScope.launch { app.exerciseRuns.add(run, token) }
    }

    fun togglePause() {
        val t = System.currentTimeMillis()
        if (paused) stepStartedAt = t else elapsedBeforePause += t - stepStartedAt
        paused = !paused
    }

    /** Velocity decays exponentially toward the step's target spin; the angle (degrees) is its integral. */
    fun ringMotion(step: ExerciseStep, at: Long): Pair<Double, Double> {
        val target = (if (step.action == ExerciseStep.Action.Hold) 1 else -1) * RING_SPEED
        val t = (if (paused) elapsedBeforePause else elapsedBeforePause + (at - stepStartedAt)) / 1000.0
        val decay = exp(-t / RING_INERTIA)
        val angle = ringBaseAngle + target * t + (ringStartVelocity - target) * RING_INERTIA * (1 - decay)
        return angle to target + (ringStartVelocity - target) * decay
    }

    fun advance(to: Int) {
        steps.getOrNull(index)?.let {
            val (angle, velocity) = ringMotion(it, System.currentTimeMillis())
            ringBaseAngle = angle
            ringStartVelocity = velocity
        }
        elapsedBeforePause = 0
        stepStartedAt = System.currentTimeMillis()
        if (to >= steps.size) {
            finished = true
            tones.play(TonePlayer.Tone.Celebration)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            record(terminatedEarly = false)
        } else {
            index = to.coerceAtLeast(0)
            // A hold starts a new repetition; switching to rest gets the short chord.
            tones.play(if (steps[index].action == ExerciseStep.Action.Hold) TonePlayer.Tone.NewRepetition else TonePlayer.Tone.Transition)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun askToExit() {
        if (finished) {
            nav.popBackStack()
            return
        }
        // Hold the timer while deciding; resume if the patient cancels.
        if (!paused) {
            togglePause()
            pausedForDialog = true
        }
        confirmingExit = true
    }

    fun cancelExit() {
        confirmingExit = false
        if (pausedForDialog) {
            pausedForDialog = false
            if (paused) togglePause()
        }
    }

    BackHandler(onBack = ::askToExit)

    LaunchedEffect(Unit) {
        if (steps.isNotEmpty()) tones.play(TonePlayer.Tone.NewRepetition)
        while (true) {
            delay(100)
            now = System.currentTimeMillis()
        }
    }

    val step = steps.getOrNull(index)
    val elapsed = if (paused) elapsedBeforePause else elapsedBeforePause + (now - stepStartedAt)
    val remaining = ((step?.duration ?: 0) * 1000L - elapsed).coerceAtLeast(0)
    val secondsLeft = ceil(remaining / 1000.0).toInt()
    // Long steps get an emphasised 3-2-1 so attention doesn't drift before the switch.
    val countingDown = (step?.duration ?: 0) > 5 && secondsLeft in 1..3

    LaunchedEffect(remaining == 0L, paused, finished) {
        if (remaining == 0L && !paused && !finished && step != null) advance(index + 1)
    }
    LaunchedEffect(secondsLeft) {
        if (countingDown && !paused && !finished) tones.play(TonePlayer.Tone.Tick)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = ::askToExit) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
                },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            if (finished || step == null) {
                Finished { nav.popBackStack() }
                if (steps.isNotEmpty()) Confetti(Modifier.fillMaxSize())
            } else {
                Running(
                    step = step,
                    index = index,
                    total = steps.size,
                    secondsLeft = secondsLeft,
                    countingDown = countingDown,
                    paused = paused,
                    ringAngle = { at -> ringMotion(step, at).first },
                    onPrevious = { advance(index - 1) },
                    onTogglePause = ::togglePause,
                    onNext = { advance(index + 1) },
                    onTerminate = ::askToExit,
                )
            }
        }
    }

    if (confirmingExit) {
        AlertDialog(
            onDismissRequest = ::cancelExit,
            title = { Text(stringResource(R.string.end_session_title)) },
            text = { Text(stringResource(R.string.end_session_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingExit = false
                    record(terminatedEarly = true)
                    nav.popBackStack()
                }) { Text(stringResource(R.string.terminate), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = ::cancelExit) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Running(
    step: ExerciseStep,
    index: Int,
    total: Int,
    secondsLeft: Int,
    countingDown: Boolean,
    paused: Boolean,
    ringAngle: (Long) -> Double,
    onPrevious: () -> Unit,
    onTogglePause: () -> Unit,
    onNext: () -> Unit,
    onTerminate: () -> Unit,
) {
    val isHold = step.action == ExerciseStep.Action.Hold
    // Same timing as iOS: an ease-in-out over 80% of the step, for both size and colour.
    val circleSpec = tween<Float>(durationMillis = step.duration.coerceAtLeast(1) * 800, easing = EaseInOut)
    val colorSpec = tween<Color>(durationMillis = step.duration.coerceAtLeast(1) * 800, easing = EaseInOut)
    val circleScale by animateFloatAsState(if (isHold) 1f else 0.45f, circleSpec, label = "circle")
    val topColor by animateColorAsState((if (isHold) HoldColors else RestColors)[0], colorSpec, label = "top")
    val bottomColor by animateColorAsState((if (isHold) HoldColors else RestColors)[1], colorSpec, label = "bottom")
    val colors = listOf(topColor, bottomColor)
    val numberScale by animateFloatAsState(if (countingDown) 1.5f else 1f, spring(), label = "number")
    // Ticks every frame so the rings turn smoothly; only the ring canvas redraws.
    var frameTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(paused) {
        while (!paused) withFrameMillis { frameTime = System.currentTimeMillis() }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinearProgressIndicator(progress = { index.toFloat() / total }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(if (isHold) R.string.squeeze_and_hold else R.string.relax),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(
                R.string.session_position,
                step.exerciseIndex + 1, step.exerciseCount, step.repetition + 1, step.repetitionCount,
                step.set + 1, step.setCount,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // Fixed-size rings just outside the full circle, counter-rotating; they never scale.
            // The wave turns inside the path so the gradient stays upright like the circle's.
            Canvas(Modifier.requiredSize(299.dp)) {
                val angle = ringAngle(frameTime) * PI / 180
                drawWavyRing(colors, diameter = 270.dp, width = 4.dp, waves = 14, rotation = angle)
                drawWavyRing(colors, diameter = 299.dp, width = 6.dp, waves = 18, rotation = -angle)
            }
            Box(
                Modifier.size(234.dp).scale(circleScale).clip(CircleShape)
                    .background(Brush.verticalGradient(colors)),
            )
            Text(
                "$secondsLeft",
                fontSize = 72.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.scale(numberScale),
            )
        }
        Text(
            if (countingDown) stringResource(if (isHold) R.string.relax_in else R.string.squeeze_in, secondsLeft) else "",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.offset(y = 20.dp),
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, enabled = index > 0) {
                Icon(Icons.Outlined.SkipPrevious, stringResource(R.string.previous), Modifier.size(36.dp))
            }
            FilledIconButton(onClick = onTogglePause, modifier = Modifier.size(72.dp)) {
                Icon(
                    if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    stringResource(if (paused) R.string.resume else R.string.pause),
                    Modifier.size(40.dp),
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Outlined.SkipNext, stringResource(R.string.next), Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onTerminate,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text(stringResource(R.string.terminate)) }
        Spacer(Modifier.height(16.dp))
    }
}

/** A circle whose edge undulates in a sine wave, stroked with the circle's vertical gradient. */
private fun DrawScope.drawWavyRing(
    colors: List<Color>,
    diameter: Dp,
    width: Dp,
    waves: Int,
    rotation: Double,
    amplitude: Dp = 4.dp,
) {
    val half = diameter.toPx() / 2
    val amp = amplitude.toPx()
    val radius = half - amp
    val segments = 360
    val path = Path()
    for (i in 0..segments) {
        val theta = i.toDouble() / segments * 2 * PI
        val r = radius + amp * sin(waves * (theta - rotation))
        val x = (center.x + r * cos(theta)).toFloat()
        val y = (center.y + r * sin(theta)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(
        path,
        Brush.verticalGradient(colors, startY = center.y - half, endY = center.y + half),
        style = Stroke(width.toPx(), cap = StrokeCap.Round),
    )
}

@Composable
private fun Finished(onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF34C759), modifier = Modifier.size(64.dp))
        Text(stringResource(R.string.well_done), style = MaterialTheme.typography.headlineSmall, color = Color(0xFF34C759))
        Text(stringResource(R.string.finished_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onDone) { Text(stringResource(R.string.done)) }
    }
}
