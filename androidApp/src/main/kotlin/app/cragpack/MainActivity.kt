package app.cragpack

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The app draws its own background under both system bars: no scrim behind 3-button navigation.
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        super.onCreate(savedInstanceState)
        val graph = (application as CragpackApp).graph
        // From Recents, Android hands back the intent that first opened the app: that link was already used.
        val fromHistory = (intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory) graph.openLink(intent?.dataString)
        setContent { App(graph) }
    }

    /** An invite link opened while the app is already running. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        (application as CragpackApp).graph.openLink(intent.dataString)
    }
}
