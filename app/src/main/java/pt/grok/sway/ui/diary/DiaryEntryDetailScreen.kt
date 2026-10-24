package pt.grok.sway.ui.diary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import pt.grok.sway.R
import pt.grok.sway.data.DiaryEntry
import pt.grok.sway.ui.LocalApp
import pt.grok.sway.ui.SectionHeader
import pt.grok.sway.ui.bowelUrgencyTitles
import pt.grok.sway.ui.effortTitles
import pt.grok.sway.ui.formatDateTime
import pt.grok.sway.ui.title
import pt.grok.sway.ui.urgencyTitles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryEntryDetailScreen(nav: NavController, id: String) {
    val app = LocalApp.current
    val entries by app.diary.items.collectAsStateWithLifecycle()
    val entry = entries.firstOrNull { it.id == id }
    LaunchedEffect(entry) { if (entry == null) nav.popBackStack() }
    if (entry == null) return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(entry.kind.title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner).verticalScroll(rememberScrollState())) {
            Row(R.string.time, entry.time.formatDateTime())
            ListItem(
                headlineContent = { Text(stringResource(R.string.synced)) },
                trailingContent = {
                    if (entry.synced) Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    else Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.tertiary)
                },
            )
            when (val d = entry.details) {
                DiaryEntry.Details.WakeUp, DiaryEntry.Details.Sleep -> Unit
                is DiaryEntry.Details.Drink -> {
                    Row(R.string.kind_drink, stringResource(d.type.title))
                    Row(R.string.volume, "${d.volumeMl} ml")
                }
                is DiaryEntry.Details.Urination -> {
                    Row(R.string.volume, if (d.volumeMl > 0) "${d.volumeMl} ml" else "–")
                    Row(R.string.urgency, stringResource(urgencyTitles[d.urgency]))
                    Row(R.string.leaked, stringResource(if (d.leaked) R.string.yes else R.string.no))
                }
                is DiaryEntry.Details.Bowel -> {
                    Row(R.string.stool_type, stringResource(R.string.stool_type_n, d.stoolType))
                    Row(R.string.urgency, stringResource(bowelUrgencyTitles[d.urgency]))
                    Row(R.string.effort, stringResource(effortTitles[d.effort]))
                    Row(R.string.time_taken, if (d.minutesTaken > 0) "${d.minutesTaken} min" else "–")
                    Row(R.string.felt_empty, stringResource(if (d.feltEmpty) R.string.yes else R.string.no))
                    Row(R.string.accident, stringResource(if (d.accident) R.string.yes else R.string.no))
                    if (d.accident && d.accidentAmount.isNotBlank()) Row(R.string.amount, d.accidentAmount)
                }
            }
            if (entry.notes.isNotBlank()) {
                SectionHeader(R.string.notes)
                Text(entry.notes, Modifier.padding(horizontal = 16.dp))
            }
            TextButton(
                onClick = { app.diary.delete(entry) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.padding(16.dp),
            ) { Text(stringResource(R.string.delete_entry)) }
            if (entry.synced) {
                Text(
                    stringResource(R.string.delete_local_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun Row(label: Int, value: String) {
    ListItem(headlineContent = { Text(stringResource(label)) }, trailingContent = { Text(value) })
}
