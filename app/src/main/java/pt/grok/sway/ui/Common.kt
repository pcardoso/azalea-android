package pt.grok.sway.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Wc
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import pt.grok.sway.R
import pt.grok.sway.SwayApplication
import pt.grok.sway.data.DiaryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

val LocalApp = staticCompositionLocalOf<SwayApplication> { error("SwayApplication not provided") }

@get:StringRes
val DiaryEntry.Kind.title: Int
    get() = when (this) {
        DiaryEntry.Kind.WakeUp -> R.string.kind_wake_up
        DiaryEntry.Kind.Sleep -> R.string.kind_sleep
        DiaryEntry.Kind.Drink -> R.string.kind_drink
        DiaryEntry.Kind.Urination -> R.string.kind_urination
        DiaryEntry.Kind.Bowel -> R.string.kind_bowel
    }

val DiaryEntry.Kind.icon: ImageVector
    get() = when (this) {
        DiaryEntry.Kind.WakeUp -> Icons.Outlined.WbTwilight
        DiaryEntry.Kind.Sleep -> Icons.Outlined.Bedtime
        DiaryEntry.Kind.Drink -> Icons.Outlined.LocalDrink
        DiaryEntry.Kind.Urination -> Icons.Outlined.WaterDrop
        DiaryEntry.Kind.Bowel -> Icons.Outlined.Wc
    }

@get:StringRes
val DiaryEntry.DrinkType.title: Int
    get() = when (this) {
        DiaryEntry.DrinkType.Water -> R.string.drink_water
        DiaryEntry.DrinkType.Coffee -> R.string.drink_coffee
        DiaryEntry.DrinkType.Tea -> R.string.drink_tea
        DiaryEntry.DrinkType.Other -> R.string.drink_other
    }

/** Urge scale shared by bladder and bowel entries (0 = none, 4 = extreme). */
val urgencyTitles = listOf(
    R.string.urgency_none, R.string.urgency_mild, R.string.urgency_moderate, R.string.urgency_strong,
    R.string.urgency_extreme,
)

/** Longer descriptions used by the bowel diary form. */
val bowelUrgencyTitles = listOf(
    R.string.bowel_urgency_0, R.string.bowel_urgency_1, R.string.bowel_urgency_2, R.string.bowel_urgency_3,
    R.string.bowel_urgency_4,
)

val effortTitles = listOf(
    R.string.effort_none, R.string.effort_minimal, R.string.effort_moderate, R.string.effort_considerable,
    R.string.effort_maximum,
)

val bristolDescriptions = listOf(
    R.string.bristol_1, R.string.bristol_2, R.string.bristol_3, R.string.bristol_4, R.string.bristol_5,
    R.string.bristol_6, R.string.bristol_7,
)

/** One-line summary shown under the entry title in lists. */
@Composable
fun DiaryEntry.summary(): String? = when (val d = details) {
    DiaryEntry.Details.WakeUp, DiaryEntry.Details.Sleep -> null
    is DiaryEntry.Details.Drink -> "${stringResource(d.type.title)} · ${d.volumeMl} ml"
    is DiaryEntry.Details.Urination -> buildList {
        if (d.volumeMl > 0) add("${d.volumeMl} ml")
        add(stringResource(urgencyTitles[d.urgency]))
        if (d.leaked) add(stringResource(R.string.leaked))
    }.joinToString(" · ")
    is DiaryEntry.Details.Bowel -> buildList {
        add(stringResource(R.string.stool_type_n, d.stoolType))
        if (d.accident) add(stringResource(R.string.accident))
    }.joinToString(" · ")
}

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun Long.formatTime(): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

fun Long.formatDateTime(): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

fun LocalDate.formatDay(): String = DateTimeFormatter.ofPattern("EEEE, d MMMM").format(this)

fun Int.formatMinutesOfDay(): String = String.format("%02d:%02d", this / 60, this % 60)

@Composable
fun SectionHeader(@StringRes title: Int) {
    androidx.compose.material3.Text(
        stringResource(title),
        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
        modifier = androidx.compose.ui.Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}
