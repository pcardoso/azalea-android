package pt.grok.sway

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.flow.MutableStateFlow
import pt.grok.sway.data.DeepLinks
import pt.grok.sway.ui.SwayRoot
import pt.grok.sway.ui.theme.SwayTheme

class MainActivity : ComponentActivity() {
    /** Prescription code from a link the app was opened with, until SwayRoot handles it. */
    private val linkedCode = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a fresh start: after rotation the intent is the same and was already handled.
        if (savedInstanceState == null) linkedCode.value = DeepLinks.prescriptionCode(intent?.data)
        setContent {
            SwayTheme {
                SwayRoot(application as SwayApplication, linkedCode)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DeepLinks.prescriptionCode(intent.data)?.let { linkedCode.value = it }
    }
}
