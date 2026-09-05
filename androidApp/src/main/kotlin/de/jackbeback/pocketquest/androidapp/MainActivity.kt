package de.jackbeback.pocketquest.androidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import de.jackbeback.pocketquest.ui.run.RunApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // RunApp's Scaffold paints the whole edge-to-edge window (status/nav bar cutouts
        // included) PAPER — a light color, so bar icons need to render dark to stay legible
        // instead of the default light-on-light that was invisible against it.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val app = application as PocketQuestApplication
        setContent {
            RunApp(
                catalog = app.catalog,
                metaRepository = app.metaRepository,
                runRepository = app.runRepository,
                pools = app.pools,
                now = { System.currentTimeMillis() },
            )
        }
    }
}
