package pt.grok.sway.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import pt.grok.sway.R
import pt.grok.sway.data.ApiException
import pt.grok.sway.ui.LocalApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(nav: NavController, linkedCode: String? = null) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf(linkedCode.orEmpty()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun signIn() {
        if (code.isBlank() || loading) return
        loading = true
        error = null
        scope.launch {
            try {
                app.session.signIn(code)
                val token = app.session.state.value.submissionToken
                app.diary.syncPending(token)
                app.exerciseRuns.syncPending(token)
                nav.popBackStack()
            } catch (e: ApiException) {
                error = if (e.status == 404) R.string.code_not_found else R.string.server_error
            } catch (e: Exception) {
                error = R.string.network_error
            } finally {
                loading = false
            }
        }
    }

    // Came from a prescription link: the code is already filled in, so sign in right away.
    LaunchedEffect(Unit) { if (linkedCode != null) signIn() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sign_in)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Outlined.Close, stringResource(R.string.cancel)) }
                },
                actions = {
                    if (loading) CircularProgressIndicator(Modifier.padding(12.dp))
                    else TextButton(onClick = ::signIn, enabled = code.isNotBlank()) { Text(stringResource(R.string.sign_in)) }
                },
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner).padding(16.dp)) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase() },
                label = { Text(stringResource(R.string.prescription_code)) },
                supportingText = { Text(stringResource(error ?: R.string.code_hint)) },
                isError = error != null,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { signIn() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(nav: NavController) {
    val app = LocalApp.current
    val session by app.session.state.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner)) {
            ListItem(headlineContent = { Text(stringResource(R.string.name)) }, trailingContent = { Text(session.name.orEmpty()) })
            ListItem(
                headlineContent = { Text(stringResource(R.string.prescription_code)) },
                trailingContent = { Text(session.code.orEmpty(), fontFamily = FontFamily.Monospace) },
            )
            TextButton(
                onClick = { confirming = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text(stringResource(R.string.sign_out)) }
            Text(
                stringResource(R.string.diary_stays),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.sign_out_question)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    app.session.signOut()
                    nav.popBackStack()
                }) { Text(stringResource(R.string.sign_out), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
