package com.edgeflow.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.edgeflow.EdgeFlowApp
import com.edgeflow.ui.navigation.EdgeFlowNavHost
import com.edgeflow.ui.theme.EdgeFlowTheme
import com.edgeflow.ui.i18n.AppLanguage

/**
 * The single activity hosting the whole UI.
 *
 * Edge-to-edge is enabled unconditionally: the app is a settings surface with
 * scrolling content, so it should paint under the system bars and let each
 * screen decide its own insets. Doing it the other way round leaves a visible
 * letterbox on gesture-navigation devices.
 */
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as EdgeFlowApp

        setContent {
            EdgeFlowTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    EdgeFlowNavHost(
                        configRepository = app.configRepository,
                        statusProbe = app.statusProbe,
                    )
                }
            }
        }
    }
}
