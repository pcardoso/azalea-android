package pt.grok.sway.ui.diary

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import pt.grok.sway.R
import pt.grok.sway.data.DiaryEntry
import pt.grok.sway.ui.LocalApp
import pt.grok.sway.ui.Routes
import pt.grok.sway.ui.formatDay
import pt.grok.sway.ui.formatTime
import pt.grok.sway.ui.icon
import pt.grok.sway.ui.summary
import pt.grok.sway.ui.title
import pt.grok.sway.ui.toLocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DiaryScreen(nav: NavController, padding: PaddingValues) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val session by app.session.state.collectAsStateWithLifecycle()
    val entries by app.diary.items.collectAsStateWithLifecycle()
    val syncing by app.diary.isSyncing.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }

    // Newest day first, newest entry first within each day.
    val days = remember(entries) {
        entries.groupBy { it.time.toLocalDate() }.toSortedMap(compareByDescending { it })
            .mapValues { (_, list) -> list.sortedByDescending { it.time } }
    }
    val pending = entries.count { !it.synced }

    Scaffold(
        modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_diary)) }) },
        floatingActionButton = {
            // The empty state has its own button.
            if (entries.isNotEmpty()) Box {
                ExtendedFloatingActionButton(
                    onClick = { menuOpen = true },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text(stringResource(R.string.add_entry)) },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DiaryEntry.Kind.entries.forEach { kind ->
                        DropdownMenuItem(
                            text = { Text(stringResource(kind.title)) },
                            leadingIcon = { Icon(kind.icon, null) },
                            onClick = {
                                menuOpen = false
                                nav.navigate(Routes.newEntry(kind))
                            },
                        )
                    }
                }
            }
        },
    ) { inner ->
        PullToRefreshBox(
            isRefreshing = syncing,
            onRefresh = { scope.launch { app.diary.syncPending(session.submissionToken) } },
            modifier = Modifier.padding(inner).fillMaxSize(),
        ) {
            if (entries.isEmpty()) {
                EmptyDiary { nav.navigate(Routes.newEntry(DiaryEntry.Kind.Drink)) }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    if (pending > 0) {
                        item {
                            ListItem(
                                headlineContent = { Text(pluralStringResource(R.plurals.entries_not_synced, pending, pending)) },
                                supportingContent = {
                                    Text(stringResource(if (session.isSignedIn) R.string.sent_when_online else R.string.sign_in_to_share))
                                },
                                leadingContent = { Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.tertiary) },
                                trailingContent = {
                                    if (session.isSignedIn) {
                                        if (syncing) CircularProgressIndicator(Modifier.padding(4.dp))
                                        else OutlinedButton(onClick = {
                                            scope.launch { app.diary.syncPending(session.submissionToken) }
                                        }) { Text(stringResource(R.string.retry)) }
                                    }
                                },
                            )
                        }
                    }
                    days.forEach { (day, dayEntries) ->
                        stickyHeader(key = day.toString()) {
                            Text(
                                day.formatDay(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .fillMaxSize(),
                            )
                        }
                        items(dayEntries, key = { it.id }) { entry ->
                            SwipeToDelete(onDelete = { app.diary.delete(entry) }) {
                                DiaryEntryRow(entry, Modifier.clickable { nav.navigate(Routes.entry(entry.id)) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) onDelete()
            it == SwipeToDismissBoxValue.EndToStart
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) { content() }
}

@Composable
fun DiaryEntryRow(entry: DiaryEntry, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier,
        headlineContent = { Text(stringResource(entry.kind.title)) },
        supportingContent = {
            val lines = listOfNotNull(entry.summary(), entry.notes.takeIf { it.isNotBlank() })
            if (lines.isNotEmpty()) Text(lines.joinToString("\n"), maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = { Icon(entry.kind.icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.time.formatTime(), style = MaterialTheme.typography.bodyMedium)
                if (!entry.synced) {
                    Icon(
                        Icons.Outlined.CloudOff, stringResource(R.string.not_synced),
                        tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun EmptyDiary(onAdd: () -> Unit) {
    // Scrollable so pull-to-refresh still works on an empty list.
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(32.dp)) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.no_entries_yet), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.no_entries_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onAdd) { Text(stringResource(R.string.add_entry)) }
            }
        }
    }
}
