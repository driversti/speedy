package dev.seniorjava.speedy.ui.dashboard

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.seniorjava.speedy.R
import dev.seniorjava.speedy.domain.DisplayMode
import dev.seniorjava.speedy.domain.ServiceState
import dev.seniorjava.speedy.domain.SpeedFormatter
import dev.seniorjava.speedy.domain.SpeedSample
import dev.seniorjava.speedy.service.SpeedNotifications

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onToggleEnabled: (Boolean) -> Unit,
    onRefreshPermissions: () -> Unit,
    onDisplayModeChanged: (DisplayMode) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MonitorSwitchCard(
                isEnabled = state.isEnabled,
                serviceState = state.serviceState,
                onToggleEnabled = onToggleEnabled,
            )
            LiveSpeedCard(
                sample = state.sample,
                isEnabled = state.isEnabled && state.serviceState == ServiceState.ACTIVE,
            )
            DisplayModeCard(
                displayMode = state.displayMode,
                onDisplayModeChanged = onDisplayModeChanged,
            )
            PermissionsSection(
                state = state.permissions,
                onRefreshPermissions = onRefreshPermissions,
            )
        }
    }
}

@Composable
private fun MonitorSwitchCard(
    isEnabled: Boolean,
    serviceState: ServiceState,
    onToggleEnabled: (Boolean) -> Unit,
) {
    val switchDescription = stringResource(R.string.dashboard_monitor_switch_description)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dashboard_monitor_switch_label),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.dashboard_status_label) + ": " +
                        serviceStateLabel(serviceState),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggleEnabled,
                modifier = Modifier.semantics { contentDescription = switchDescription },
            )
        }
    }
}

@Composable
private fun serviceStateLabel(state: ServiceState): String = when (state) {
    ServiceState.ACTIVE -> stringResource(R.string.dashboard_status_active)
    ServiceState.WAITING_FOR_NETWORK -> stringResource(R.string.dashboard_status_waiting)
    ServiceState.STOPPED -> stringResource(R.string.dashboard_status_stopped)
}

@Composable
private fun LiveSpeedCard(sample: SpeedSample, isEnabled: Boolean) {
    val formatter = remember { SpeedFormatter() }
    val placeholder = stringResource(R.string.dashboard_speed_placeholder)
    val download = if (isEnabled) formatter.formatFull(sample.downloadBps) else placeholder
    val upload = if (isEnabled) formatter.formatFull(sample.uploadBps) else placeholder

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.dashboard_live_speed_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SpeedCell(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.dashboard_download_label),
                    value = download,
                    icon = Icons.Filled.ArrowDownward,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                SpeedCell(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.dashboard_upload_label),
                    value = upload,
                    icon = Icons.Filled.ArrowUpward,
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun SpeedCell(
    modifier: Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PermissionsSection(
    state: PermissionsState,
    onRefreshPermissions: () -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.permissions_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { onRefreshPermissions() },
            )
            PermissionCard(
                title = stringResource(R.string.permission_notifications_title),
                description = if (state.notificationsGranted) {
                    stringResource(R.string.permission_notifications_granted)
                } else {
                    stringResource(R.string.permission_notifications_missing)
                },
                icon = if (state.notificationsGranted) {
                    Icons.Filled.Notifications
                } else {
                    Icons.Filled.NotificationsOff
                },
                isAlert = !state.notificationsGranted,
                actionLabel = if (state.notificationsGranted) {
                    null
                } else {
                    stringResource(R.string.permission_notifications_cta)
                },
                onAction = {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            )
        }

        if (!state.channelEnabled) {
            PermissionCard(
                title = stringResource(R.string.permission_channel_disabled_title),
                description = stringResource(R.string.permission_channel_disabled_description),
                icon = Icons.Filled.NotificationsOff,
                isAlert = true,
                actionLabel = stringResource(R.string.permission_channel_disabled_cta),
                onAction = {
                    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        putExtra(Settings.EXTRA_CHANNEL_ID, SpeedNotifications.CHANNEL_ID)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                },
            )
        }

        PermissionCard(
            title = stringResource(R.string.permission_battery_title),
            description = if (state.batteryOptimizationIgnored) {
                stringResource(R.string.permission_battery_ignored)
            } else {
                stringResource(R.string.permission_battery_description)
            },
            icon = Icons.Filled.BatterySaver,
            isAlert = false,
            actionLabel = if (state.batteryOptimizationIgnored) {
                null
            } else {
                stringResource(R.string.permission_battery_cta)
            },
            onAction = {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isAlert: Boolean,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    val colors = if (isAlert) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    } else {
        CardDefaults.cardColors()
    }
    Card(colors = colors, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = description, style = MaterialTheme.typography.bodyLarge)
            if (actionLabel != null) {
                Spacer(modifier = Modifier.height(12.dp))
                if (isAlert) {
                    Button(onClick = onAction) { Text(actionLabel) }
                } else {
                    OutlinedButton(onClick = onAction) { Text(actionLabel) }
                }
            }
        }
    }
}

@Composable
private fun DisplayModeCard(
    displayMode: DisplayMode,
    onDisplayModeChanged: (DisplayMode) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.dashboard_display_mode_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            DisplayMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDisplayModeChanged(mode) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = displayMode == mode,
                        onClick = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(displayModeLabel(mode)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

private fun displayModeLabel(mode: DisplayMode): Int = when (mode) {
    DisplayMode.BOTH -> R.string.dashboard_display_mode_both
    DisplayMode.DOWNLOAD -> R.string.dashboard_display_mode_download
    DisplayMode.UPLOAD -> R.string.dashboard_display_mode_upload
}
