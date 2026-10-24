package pt.grok.sway.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** Synthesised chords for the guided exercise session, same notes as the iOS app. */
class TonePlayer {
    enum class Tone {
        /** Short C major chord when switching between hold and rest. */
        Transition,
        /** Rising I–IV–V progression (C, F, G) when a new repetition starts. */
        NewRepetition,
        /** Countdown tick for the last 3 seconds of a long step. */
        Tick,
        /** "Ta-da": quick rising arpeggio into a held C major chord when the session is done. */
        Celebration,
    }

    private val buffers: Map<Tone, ShortArray> = mapOf(
        Tone.Transition to render(listOf(listOf(C5, E5, G5) to 0.25), 0.45),
        Tone.NewRepetition to render(
            listOf(listOf(C5, E5, G5) to 0.18, listOf(F5, A5, C6) to 0.18, listOf(G5, B5, D6) to 0.4), 0.8,
        ),
        Tone.Tick to render(listOf(listOf(660.0) to 0.08), 0.35),
        Tone.Celebration to render(
            listOf(
                listOf(C5) to 0.09, listOf(E5) to 0.09, listOf(G5) to 0.09,
                listOf(C5, E5, G5, C6, C6 * 1.25) to 1.1,
            ),
            0.8,
        ),
    )

    private var track: AudioTrack? = null

    fun play(tone: Tone) {
        val samples = buffers.getValue(tone)
        track?.release()
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
            .apply {
                write(samples, 0, samples.size)
                play()
            }
    }

    fun release() {
        track?.release()
        track = null
    }

    companion object {
        private const val SAMPLE_RATE = 44_100
        private const val C5 = 523.25
        private const val E5 = 659.25
        private const val F5 = 698.46
        private const val G5 = 783.99
        private const val A5 = 880.0
        private const val B5 = 987.77
        private const val C6 = 1046.5
        private const val D6 = 1174.66

        /** Renders (chord frequencies, seconds) segments back to back with a bell-like decay. */
        private fun render(segments: List<Pair<List<Double>, Double>>, volume: Double): ShortArray {
            val out = ArrayList<Short>()
            for ((chord, seconds) in segments) {
                val frames = (seconds * SAMPLE_RATE).toInt()
                val attack = min((0.01 * SAMPLE_RATE).toInt(), frames / 2).coerceAtLeast(1)
                for (i in 0 until frames) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val rise = min(1.0, i.toDouble() / attack)
                    val decay = exp(-4 * t / seconds) * (frames - i) / frames
                    val value = chord.sumOf { sin(2 * PI * it * t) } / chord.size
                    out.add((value * rise * decay * volume * Short.MAX_VALUE).toInt().toShort())
                }
            }
            return out.toShortArray()
        }
    }
}
