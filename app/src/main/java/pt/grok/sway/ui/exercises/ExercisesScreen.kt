package pt.grok.sway.ui.exercises

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import pt.grok.sway.R
import pt.grok.sway.data.Prescription
import pt.grok.sway.ui.LocalApp
import pt.grok.sway.ui.Routes
import pt.grok.sway.ui.SectionHeader
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(nav: NavController, padding: PaddingValues) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val session by app.session.state.collectAsStateWithLifecycle()
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            loading = true
            failed = runCatching { app.session.refreshPrescription() }.isFailure
            loading = false
        }
    }
    LaunchedEffect(session.code) { if (session.isSignedIn && session.prescription == null) load() }

    Scaffold(
        modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_exercises)) }) },
    ) { inner ->
        val prescription = session.prescription
        Column(Modifier.padding(inner).fillMaxSize()) {
            when {
                prescription != null -> PullToRefreshBox(isRefreshing = loading, onRefresh = ::load) {
                    PrescriptionContent(prescription) { nav.navigate(Routes.SESSION) }
                }
                !session.isSignedIn -> Placeholder(
                    Icons.Outlined.SelfImprovement, R.string.no_prescription, R.string.no_prescription_body,
                    R.string.enter_code,
                ) { nav.navigate(Routes.SIGN_IN) }
                failed && !loading -> Placeholder(
                    Icons.Outlined.WifiOff, R.string.load_failed, R.string.load_failed_body, R.string.try_again, ::load,
                )
                else -> Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun PrescriptionContent(prescription: Prescription, onStart: () -> Unit) {
    val context = LocalContext.current
    val api = LocalApp.current.api
    val exercises = prescription.exercises.sortedBy { it.index }
    val seconds = prescription.exerciseSequence.sumOf { it.duration }

    LazyColumn(Modifier.fillMaxSize()) {
        prescription.prescription.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            item { SectionHeader(R.string.from_your_therapist) }
            item { Text(notes, Modifier.padding(horizontal = 16.dp)) }
        }
        if (exercises.isNotEmpty()) {
            item { SectionHeader(R.string.tab_exercises) }
            exercises.forEachIndexed { index, exercise ->
                item {
                    ListItem(
                        headlineContent = {
                            Text(exercise.instructions.ifBlank { stringResource(R.string.exercise_n, index + 1) })
                        },
                        supportingContent = {
                            Text(stringResource(R.string.exercise_summary, exercise.repetitions, exercise.sets, exercise.hold, exercise.rest))
                        },
                    )
                }
            }
            item {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Icon(Icons.Outlined.PlayArrow, null)
                    Text(stringResource(R.string.start_session), Modifier.padding(start = 8.dp))
                }
            }
            item {
                val minutes = (seconds / 60.0).roundToInt().coerceAtLeast(1)
                Text(
                    pluralStringResource(R.plurals.about_minutes, minutes, minutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        val lifestyle = prescription.lifestyle.sortedBy { it.index }
        if (lifestyle.isNotEmpty()) {
            item { SectionHeader(R.string.lifestyle) }
            lifestyle.forEach { tip ->
                item {
                    ListItem(
                        headlineContent = { Text(tip.instructions) },
                        leadingContent = { Icon(Icons.Outlined.Spa, null, tint = MaterialTheme.colorScheme.primary) },
                    )
                }
            }
        }
        val documents = prescription.documents.sortedBy { it.index }
        if (documents.isNotEmpty()) {
            item { SectionHeader(R.string.documents) }
            documents.forEach { document ->
                item {
                    ListItem(
                        headlineContent = { Text(document.title, color = MaterialTheme.colorScheme.primary) },
                        leadingContent = { Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(api.fileUrl(document)))
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun Placeholder(icon: ImageVector, title: Int, body: Int, action: Int, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAction) { Text(stringResource(action)) }
    }
}
