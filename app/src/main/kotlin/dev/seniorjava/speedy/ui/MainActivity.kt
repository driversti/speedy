package dev.seniorjava.speedy.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.seniorjava.speedy.ui.dashboard.DashboardScreen
import dev.seniorjava.speedy.ui.dashboard.DashboardViewModel
import dev.seniorjava.speedy.ui.theme.SpeedyTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpeedyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val uiState by viewModel.state.collectAsStateWithLifecycle()
                    DashboardScreen(
                        state = uiState,
                        onToggleEnabled = viewModel::onToggleEnabled,
                        onRefreshPermissions = viewModel::refreshPermissions,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Permissions can change while the Dashboard is backgrounded
        // (Settings → permissions) — re-read on every resume.
        viewModel.refreshPermissions()
    }
}
