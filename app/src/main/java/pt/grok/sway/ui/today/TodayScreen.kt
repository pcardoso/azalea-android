package pt.grok.sway.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Wc
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import pt.grok.sway.R
import pt.grok.sway.data.DiaryEntry
import pt.grok.sway.ui.LocalApp
import pt.grok.sway.ui.Routes
import pt.grok.sway.ui.SectionHeader
import pt.grok.sway.ui.icon
import pt.grok.sway.ui.title
import pt.grok.sway.ui.toLocalDate
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(nav: NavController, padding: PaddingValues) {
    val app = LocalApp.current
    val session by app.session.state.collectAsStateWithLifecycle()
    val entries by app.diary.items.collectAsStateWithLifecycle()
    val today = remember(entries) { entries.filter { it.time.toLocalDate() == LocalDate.now() } }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val fluids = today.sumOf { (it.details as? DiaryEntry.Details.Drink)?.volumeMl ?: 0 }
    val leaks = today.count {
        val d = it.details
        (d is DiaryEntry.Details.Urination && d.leaked) || (d is DiaryEntry.Details.Bowel && d.accident)
    }

    androidx.compose.material3.Scaffold(
        modifier = Modifier.padding(bottom = padding.calculateBottomPadding()).nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        session.name?.let { stringResource(R.string.hello_name, it) }
                            ?: stringResource(R.string.welcome_to_sway)
                    )
                },
                actions = {
                    IconButton(onClick = { nav.navigate(if (session.isSignedIn) Routes.ACCOUNT else Routes.SIGN_IN) }) {
                        Icon(Icons.Outlined.AccountCircle, contentDescription = stringResource(R.string.account))
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { inner ->
        LazyColumn(contentPadding = inner) {
            if (!session.isSignedIn) {
                item {
                    Card(Modifier.padding(16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Outlined.Key, null)
                                Text(stringResource(R.string.connect_title), style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                stringResource(R.string.connect_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = { nav.navigate(Routes.SIGN_IN) }) { Text(stringResource(R.string.enter_code)) }
                        }
                    }
                }
            }

            item { SectionHeader(R.string.today) }
            item { StatRow(Icons.Outlined.WaterDrop, R.string.fluids, "$fluids ml") }
            item { StatRow(Icons.Outlined.WaterDrop, R.string.urinations, today.count { it.kind == DiaryEntry.Kind.Urination }.toString()) }
            item { StatRow(Icons.Outlined.Warning, R.string.leaks, leaks.toString()) }
            item { StatRow(Icons.Outlined.Wc, R.string.bowel_movements, today.count { it.kind == DiaryEntry.Kind.Bowel }.toString()) }

            item { SectionHeader(R.string.quick_add) }
            listOf(DiaryEntry.Kind.Drink, DiaryEntry.Kind.Urination, DiaryEntry.Kind.Bowel).forEach { kind ->
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(kind.title), color = MaterialTheme.colorScheme.primary) },
                        leadingContent = { Icon(kind.icon, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { nav.navigate(Routes.newEntry(kind)) },
                    )
                }
            }

            session.prescription?.takeIf { it.exercises.isNotEmpty() }?.let { prescription ->
                item { SectionHeader(R.string.tab_exercises) }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.todays_exercises)) },
                        supportingContent = {
                            Text(pluralStringResource(R.plurals.exercise_count, prescription.exercises.size, prescription.exercises.size))
                        },
                        leadingContent = { Icon(Icons.Outlined.SelfImprovement, null) },
                        trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                        modifier = Modifier.clickable { nav.navigate("exercises") },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatRow(icon: ImageVector, label: Int, value: String) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Text(value, style = MaterialTheme.typography.bodyLarge) },
    )
}
