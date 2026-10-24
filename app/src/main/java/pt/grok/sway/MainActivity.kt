package pt.grok.sway

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pt.grok.sway.ui.SwayRoot
import pt.grok.sway.ui.theme.SwayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SwayTheme {
                SwayRoot(application as SwayApplication)
            }
        }
    }
}
