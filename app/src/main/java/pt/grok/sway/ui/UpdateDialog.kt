package pt.grok.sway.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.grok.sway.BuildConfig
import pt.grok.sway.R
import pt.grok.sway.data.AppRelease

/** Offers a newer sideloaded APK; the browser downloads it and hands it to the system installer. */
@Composable
fun UpdateDialog(release: AppRelease) {
    val app = LocalApp.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { app.updates.dismissPrompt() },
        title = { Text(stringResource(R.string.update_title)) },
        text = {
            Column {
                Text(stringResource(R.string.update_body, release.versionName, BuildConfig.VERSION_NAME))
                release.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.url)))
                app.updates.dismissPrompt()
            }) { Text(stringResource(R.string.update_install)) }
        },
        dismissButton = {
            TextButton(onClick = { app.updates.dismissPrompt() }) { Text(stringResource(R.string.update_later)) }
        },
    )
}
