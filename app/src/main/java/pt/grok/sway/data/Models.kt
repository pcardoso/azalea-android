package pt.grok.sway.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** A single bladder or bowel diary record, stored locally and synced to the backend. */
@Serializable
data class DiaryEntry(
    val id: String = UUID.randomUUID().toString(),
    /** Epoch milliseconds. */
    val time: Long,
    val notes: String = "",
    val synced: Boolean = false,
    val details: Details,
) {
    @Serializable
    sealed interface Details {
        @Serializable @SerialName("wakeUp")
        data object WakeUp : Details

        @Serializable @SerialName("sleep")
        data object Sleep : Details

        @Serializable @SerialName("drink")
        data class Drink(val type: DrinkType, val volumeMl: Int) : Details

        @Serializable @SerialName("urination")
        data class Urination(val volumeMl: Int, val urgency: Int, val leaked: Boolean) : Details

        @Serializable @SerialName("bowel")
        data class Bowel(
            val urgency: Int = 0,
            val accident: Boolean = false,
            val accidentAmount: String = "",
            /** Minutes spent, 0 when not recorded. */
            val minutesTaken: Int = 0,
            /** Bristol stool scale, 1..7. */
            val stoolType: Int = 4,
            val effort: Int = 0,
            val feltEmpty: Boolean = true,
        ) : Details
    }

    val kind: Kind
        get() = when (details) {
            Details.WakeUp -> Kind.WakeUp
            Details.Sleep -> Kind.Sleep
            is Details.Drink -> Kind.Drink
            is Details.Urination -> Kind.Urination
            is Details.Bowel -> Kind.Bowel
        }

    enum class Kind { WakeUp, Sleep, Drink, Urination, Bowel }

    @Serializable
    enum class DrinkType(val apiValue: String) {
        @SerialName("water") Water("water"),
        @SerialName("coffee") Coffee("coffee"),
        @SerialName("tea") Tea("tea"),
        @SerialName("other") Other("other"),
    }
}

/** One guided exercise session, recorded when it finishes or is terminated early. */
@Serializable
data class ExerciseRun(
    val id: String = UUID.randomUUID().toString(),
    val prescriptionId: Int?,
    val startedAt: Long,
    val endedAt: Long,
    /** Hold/rest steps fully completed. */
    val completedSteps: Int,
    val totalSteps: Int,
    val terminatedEarly: Boolean,
    val synced: Boolean = false,
)

/** Public prescription payload returned by `GET /pub/prescriptions/:code`. */
@Serializable
data class Prescription(
    val client: Client,
    val prescription: Details,
    val exercises: List<Exercise> = emptyList(),
    val lifestyle: List<LifestyleTip> = emptyList(),
    val documents: List<Document> = emptyList(),
    val clientSubmissionToken: String? = null,
) {
    @Serializable
    data class Client(val name: String, val preferredName: String? = null) {
        val displayName: String get() = preferredName?.takeIf { it.isNotBlank() } ?: name
    }

    @Serializable
    data class Details(val id: Int, val code: String, val notes: String? = null)

    @Serializable
    data class Exercise(
        val id: Int,
        val instructions: String = "",
        val repetitions: Int,
        val sets: Int,
        /** Seconds to hold each contraction. */
        val hold: Int,
        /** Seconds to rest between contractions. */
        val rest: Int,
        val index: Int = 0,
    )

    @Serializable
    data class LifestyleTip(val id: Int, val instructions: String, val index: Int = 0)

    @Serializable
    data class Document(val id: Int, val title: String, val fileId: String, val index: Int = 0)

    /** Flattens every exercise into a hold/rest sequence: each set runs all its repetitions. */
    val exerciseSequence: List<ExerciseStep>
        get() {
            val sorted = exercises.sortedBy { it.index }
            return buildList {
                sorted.forEachIndexed { exerciseIndex, exercise ->
                    for (set in 0 until exercise.sets.coerceAtLeast(0)) {
                        for (rep in 0 until exercise.repetitions.coerceAtLeast(0)) {
                            for ((action, duration) in listOf(
                                ExerciseStep.Action.Hold to exercise.hold,
                                ExerciseStep.Action.Rest to exercise.rest,
                            )) {
                                add(
                                    ExerciseStep(
                                        action, duration, exerciseIndex, sorted.size,
                                        rep, exercise.repetitions, set, exercise.sets,
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
}

/** One hold or rest interval in a guided exercise session. */
data class ExerciseStep(
    val action: Action,
    val duration: Int,
    val exerciseIndex: Int,
    val exerciseCount: Int,
    val repetition: Int,
    val repetitionCount: Int,
    val set: Int,
    val setCount: Int,
) {
    enum class Action { Hold, Rest }
}
