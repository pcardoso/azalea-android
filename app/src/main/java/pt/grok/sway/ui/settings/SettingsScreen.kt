package pt.grok.sway.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import pt.grok.sway.BuildConfig
import pt.grok.sway.R
import pt.grok.sway.data.ReminderSettings
import pt.grok.sway.ui.LocalApp
import pt.grok.sway.ui.Routes
import pt.grok.sway.ui.SectionHeader
import pt.grok.sway.ui.formatMinutesOfDay
import java.time.Year

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController, padding: PaddingValues) {
    val app = LocalApp.current
    val context = LocalContext.current
    val session by app.session.state.collectAsStateWithLifecycle()
    val reminders by app.reminders.state.collectAsStateWithLifecycle()
    var editingTime by remember { mutableStateOf<Pair<Int, (Int) -> ReminderSettings.State>?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    var pendingChange by remember { mutableStateOf<((ReminderSettings.State) -> ReminderSettings.State)?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        pendingChange?.takeIf { granted }?.let { change -> app.reminders.update(change) }
        pendingChange = null
    }

    /** Turning a reminder on asks for the notifications permission first (Android 13+). */
    fun enable(change: (ReminderSettings.State) -> ReminderSettings.State) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingChange = change
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            app.reminders.update(change)
        }
    }

    Scaffold(
        modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) },
    ) { inner ->
        Column(Modifier.padding(inner).verticalScroll(rememberScrollState())) {
            SectionHeader(R.string.account)
            if (session.isSignedIn) {
                ListItem(
                    headlineContent = { Text(session.name.orEmpty()) },
                    supportingContent = { Text(session.code.orEmpty()) },
                    leadingContent = { Icon(Icons.Outlined.AccountCircle, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                    modifier = Modifier.clickable { nav.navigate(Routes.ACCOUNT) },
                )
            } else {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.enter_prescription_code), color = MaterialTheme.colorScheme.primary) },
                    leadingContent = { Icon(Icons.Outlined.Key, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { nav.navigate(Routes.SIGN_IN) },
                )
            }

            SectionHeader(R.string.reminders)
            ListItem(
                headlineContent = { Text(stringResource(R.string.drink_water_reminder)) },
                leadingContent = { Icon(Icons.Outlined.WaterDrop, null) },
                trailingContent = {
                    Switch(reminders.waterEnabled, onCheckedChange = { on ->
                        if (on) enable { it.copy(waterEnabled = true) } else app.reminders.update { it.copy(waterEnabled = false) }
                    })
                },
            )
            if (reminders.waterEnabled) {
                TimeRow(R.string.from, reminders.waterStart) { editingTime = reminders.waterStart to { m -> reminders.copy(waterStart = m) } }
                TimeRow(R.string.until, reminders.waterEnd) { editingTime = reminders.waterEnd to { m -> reminders.copy(waterEnd = m) } }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.every)) },
                    trailingContent = {
                        Row {
                            (1..4).forEach { hours ->
                                FilterChip(
                                    selected = reminders.waterIntervalHours == hours,
                                    onClick = { app.reminders.update { it.copy(waterIntervalHours = hours) } },
                                    label = { Text("$hours h") },
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.exercise_reminder)) },
                leadingContent = { Icon(Icons.Outlined.SelfImprovement, null) },
                trailingContent = {
                    Switch(reminders.exerciseEnabled, onCheckedChange = { on ->
                        if (on) enable { it.copy(exerciseEnabled = true) } else app.reminders.update { it.copy(exerciseEnabled = false) }
                    })
                },
            )
            if (reminders.exerciseEnabled) {
                TimeRow(R.string.time, reminders.exerciseTime) { editingTime = reminders.exerciseTime to { m -> reminders.copy(exerciseTime = m) } }
            }
            if (permissionDenied) {
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text(stringResource(R.string.notifications_off)) }
            }

            SectionHeader(R.string.about)
            ListItem(
                headlineContent = { Text(stringResource(R.string.credits)) },
                trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                modifier = Modifier.clickable { nav.navigate(Routes.CREDITS) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.version)) },
                trailingContent = { Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") },
            )
        }
    }

    editingTime?.let { (initial, apply) ->
        val state = rememberTimePickerState(initial / 60, initial % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { editingTime = null },
            confirmButton = {
                TextButton(onClick = {
                    val minutes = state.hour * 60 + state.minute
                    app.reminders.update { apply(minutes) }
                    editingTime = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { editingTime = null }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
private fun TimeRow(label: Int, minutes: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        trailingContent = { OutlinedButton(onClick = onClick) { Text(minutes.formatMinutesOfDay()) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsScreen(nav: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.credits)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner).padding(16.dp)) {
            Text(stringResource(R.string.credits_body))
            Text(
                stringResource(R.string.copyright, Year.now().value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
