package pt.grok.sway.ui.diary

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import pt.grok.sway.R
import pt.grok.sway.data.DiaryEntry
import pt.grok.sway.ui.LocalApp
import pt.grok.sway.ui.SectionHeader
import pt.grok.sway.ui.bowelUrgencyTitles
import pt.grok.sway.ui.bristolDescriptions
import pt.grok.sway.ui.effortTitles
import pt.grok.sway.ui.formatDateTime
import pt.grok.sway.ui.title
import pt.grok.sway.ui.urgencyTitles
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

private val quickVolumes = listOf(150, 250, 330, 500)

/** Full-screen form for logging a new diary entry of any kind. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryEntryFormScreen(nav: NavController, initialKind: DiaryEntry.Kind) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()

    var kind by remember { mutableStateOf(initialKind) }
    var time by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var notes by remember { mutableStateOf("") }
    var drinkType by remember { mutableStateOf(DiaryEntry.DrinkType.Water) }
    var volume by remember { mutableStateOf("") }
    var urgency by remember { mutableIntStateOf(0) }
    var leaked by remember { mutableStateOf(false) }
    var bowel by remember { mutableStateOf(DiaryEntry.Details.Bowel()) }
    var saving by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var showingBristol by remember { mutableStateOf(false) }

    val volumeMl = volume.toIntOrNull() ?: 0
    val canSave = !saving && (kind != DiaryEntry.Kind.Drink || volumeMl > 0)

    fun save() {
        val details = when (kind) {
            DiaryEntry.Kind.WakeUp -> DiaryEntry.Details.WakeUp
            DiaryEntry.Kind.Sleep -> DiaryEntry.Details.Sleep
            DiaryEntry.Kind.Drink -> DiaryEntry.Details.Drink(drinkType, volumeMl)
            DiaryEntry.Kind.Urination -> DiaryEntry.Details.Urination(volumeMl, urgency, leaked)
            DiaryEntry.Kind.Bowel -> bowel
        }
        saving = true
        scope.launch {
            // Saved locally right away; a failed upload is retried later from the diary list.
            app.diary.add(DiaryEntry(time = time, notes = notes.trim(), details = details), app.session.state.value.submissionToken)
            nav.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(kind.title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Outlined.Close, stringResource(R.string.cancel)) }
                },
                actions = { TextButton(onClick = ::save, enabled = canSave) { Text(stringResource(R.string.save)) } },
            )
        },
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DiaryEntry.Kind.entries.forEach { k ->
                    FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(stringResource(k.title)) })
                }
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.time)) },
                trailingContent = { OutlinedButton(onClick = { pickingTime = true }) { Text(time.formatDateTime()) } },
            )

            when (kind) {
                DiaryEntry.Kind.WakeUp, DiaryEntry.Kind.Sleep -> Unit
                DiaryEntry.Kind.Drink -> {
                    SectionHeader(R.string.kind_drink)
                    Segmented(DiaryEntry.DrinkType.entries.map { stringResource(it.title) }, drinkType.ordinal) {
                        drinkType = DiaryEntry.DrinkType.entries[it]
                    }
                    VolumeField(volume) { volume = it }
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        quickVolumes.forEach { ml ->
                            FilterChip(selected = volumeMl == ml, onClick = { volume = ml.toString() }, label = { Text("$ml") })
                        }
                    }
                }
                DiaryEntry.Kind.Urination -> {
                    VolumeField(volume, supporting = stringResource(R.string.volume_optional)) { volume = it }
                    SectionHeader(R.string.urgency)
                    Segmented(urgencyTitles.map { stringResource(it) }, urgency) { urgency = it }
                    SwitchRow(R.string.leaked, leaked) { leaked = it }
                }
                DiaryEntry.Kind.Bowel -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader(R.string.bristol_scale)
                        IconButton(onClick = { showingBristol = true }) {
                            Icon(Icons.Outlined.Info, stringResource(R.string.about_bristol), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    (1..7).forEach { type ->
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.stool_type_n, type)) },
                            supportingContent = { Text(stringResource(bristolDescriptions[type - 1])) },
                            leadingContent = {
                                androidx.compose.material3.RadioButton(
                                    selected = bowel.stoolType == type,
                                    onClick = { bowel = bowel.copy(stoolType = type) },
                                )
                            },
                        )
                    }
                    SectionHeader(R.string.urgency)
                    bowelUrgencyTitles.forEachIndexed { index, label ->
                        ListItem(
                            headlineContent = { Text(stringResource(label)) },
                            leadingContent = {
                                androidx.compose.material3.RadioButton(
                                    selected = bowel.urgency == index,
                                    onClick = { bowel = bowel.copy(urgency = index) },
                                )
                            },
                        )
                    }
                    SectionHeader(R.string.effort)
                    Segmented(effortTitles.map { stringResource(it) }, bowel.effort) { bowel = bowel.copy(effort = it) }
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.time_taken)) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { bowel = bowel.copy(minutesTaken = (bowel.minutesTaken - 1).coerceAtLeast(0)) }) { Text("−") }
                                Text(if (bowel.minutesTaken > 0) "${bowel.minutesTaken} min" else "–")
                                TextButton(onClick = { bowel = bowel.copy(minutesTaken = (bowel.minutesTaken + 1).coerceAtMost(120)) }) { Text("+") }
                            }
                        },
                    )
                    SwitchRow(R.string.felt_empty_afterwards, bowel.feltEmpty) { bowel = bowel.copy(feltEmpty = it) }
                    SwitchRow(R.string.accident, bowel.accident) { bowel = bowel.copy(accident = it) }
                    if (bowel.accident) {
                        OutlinedTextField(
                            value = bowel.accidentAmount,
                            onValueChange = { bowel = bowel.copy(accidentAmount = it) },
                            label = { Text(stringResource(R.string.accident_amount)) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                }
            }

            SectionHeader(R.string.notes)
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text(stringResource(R.string.optional)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
    }

    if (pickingTime) {
        DateTimePickerDialog(time, onDismiss = { pickingTime = false }) {
            time = it.coerceAtMost(System.currentTimeMillis())
            pickingTime = false
        }
    }
    if (showingBristol) {
        AlertDialog(
            onDismissRequest = { showingBristol = false },
            confirmButton = { TextButton(onClick = { showingBristol = false }) { Text(stringResource(R.string.done)) } },
            title = { Text(stringResource(R.string.bristol_stool_scale)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { Text("${stringResource(R.string.stool_type_n, it)}: ${stringResource(bristolDescriptions[it - 1])}") }
                    Text(stringResource(R.string.bristol_footer), style = MaterialTheme.typography.bodySmall)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selected,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                label = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

@Composable
private fun VolumeField(value: String, supporting: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter(Char::isDigit).take(4)) },
        label = { Text(stringResource(R.string.volume)) },
        suffix = { Text("ml") },
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SwitchRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

/** Date picker followed by a time picker, returning epoch milliseconds in the local zone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(initial: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val initialDateTime = Instant.ofEpochMilli(initial).atZone(zone).toLocalDateTime()
    var pickedDate by remember { mutableStateOf<java.time.LocalDate?>(null) }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initialDateTime.toLocalDate().atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
    )
    val timeState = rememberTimePickerState(initialDateTime.hour, initialDateTime.minute)

    val date = pickedDate
    if (date == null) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    pickedDate = dateState.selectedDateMillis
                        ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate() }
                        ?: initialDateTime.toLocalDate()
                }) { Text(stringResource(R.string.next)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(dateState) }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    onConfirm(LocalDateTime.of(date, java.time.LocalTime.of(timeState.hour, timeState.minute)).atZone(zone).toInstant().toEpochMilli())
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(timeState) },
        )
    }
}
