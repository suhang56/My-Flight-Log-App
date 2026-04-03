package com.flightlog.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flightlog.app.R
import com.flightlog.app.data.backup.BackupMetadata
import com.flightlog.app.ui.settings.SettingsViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToAddFlight: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRestoreDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.clearSnackbar()
        }
    }

    if (showRestoreDialog) {
        RestoreConfirmDialog(
            onConfirm = {
                showRestoreDialog = false
                viewModel.restore()
            },
            onDismiss = { showRestoreDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.profile_settings))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // -- Header Card --
            val authUser = uiState.authUser
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar
                    val initials = authUser?.displayName
                        ?.split(" ")
                        ?.take(2)
                        ?.mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                        ?.joinToString("")

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        if (initials.isNullOrEmpty()) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        } else {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = authUser?.displayName ?: stringResource(R.string.profile_guest),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (authUser?.email != null) {
                        Text(
                            text = authUser.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (authUser != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val provider = when {
                            authUser.isGoogleProvider -> "Google"
                            else -> "Email"
                        }
                        SuggestionChip(
                            onClick = {},
                            label = { Text(provider) }
                        )
                    }
                }
            }

            // -- Add Flight Button --
            Button(
                onClick = onNavigateToAddFlight,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.profile_add_flight_manually))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // -- Account Section --
            SectionHeader(stringResource(R.string.profile_section_account))

            if (authUser != null) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.profile_signed_in_as)) },
                    supportingContent = { Text(authUser.email ?: authUser.displayName ?: "Unknown") }
                )
                ListItem(
                    headlineContent = {
                        OutlinedButton(onClick = { viewModel.signOut() }) {
                            Text(stringResource(R.string.profile_sign_out))
                        }
                    }
                )
            } else {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.profile_sign_in_prompt)) },
                    supportingContent = { Text(stringResource(R.string.profile_sign_in_providers)) }
                )
                Button(
                    onClick = onNavigateToLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.profile_sign_in))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // -- Backup Section --
            SectionHeader(stringResource(R.string.profile_section_backup))

            val isGoogleUser = authUser?.isGoogleProvider == true

            if (!isGoogleUser && authUser != null) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.profile_drive_requires_google)) },
                    supportingContent = { Text(stringResource(R.string.profile_drive_sign_in_hint)) }
                )
            } else {
                val metadata = uiState.backupMetadata
                if (metadata != null) {
                    BackupInfoItem(metadata)
                } else {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.profile_no_backup_yet)) },
                        supportingContent = { Text(stringResource(R.string.profile_backup_hint)) }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.backupNow() },
                        enabled = isGoogleUser && !uiState.isBackingUp && !uiState.isRestoring,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (uiState.isBackingUp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.profile_back_up_now))
                    }

                    OutlinedButton(
                        onClick = { showRestoreDialog = true },
                        enabled = isGoogleUser && !uiState.isBackingUp && !uiState.isRestoring,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (uiState.isRestoring) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(
                                Icons.Default.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.profile_restore))
                    }
                }

                if (authUser == null) {
                    Text(
                        text = stringResource(R.string.profile_sign_in_for_backup),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // -- About Section --
            SectionHeader(stringResource(R.string.profile_section_about))

            ListItem(
                headlineContent = { Text(stringResource(R.string.profile_version_label)) },
                supportingContent = { Text(stringResource(R.string.profile_version_value)) }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun BackupInfoItem(metadata: BackupMetadata) {
    val dateStr = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(metadata.lastBackupAt))
    val sizeStr = when {
        metadata.fileSizeBytes < 1024 -> "${metadata.fileSizeBytes} B"
        metadata.fileSizeBytes < 1024 * 1024 -> "${metadata.fileSizeBytes / 1024} KB"
        else -> "%.1f MB".format(metadata.fileSizeBytes / (1024.0 * 1024.0))
    }

    ListItem(
        leadingContent = {
            Icon(
                Icons.Default.CloudDone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        headlineContent = { Text(stringResource(R.string.profile_last_backup_format, dateStr)) },
        supportingContent = { Text(stringResource(R.string.profile_backup_summary_format, metadata.flightCount, sizeStr)) }
    )
}

@Composable
private fun RestoreConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_restore_dialog_title)) },
        text = {
            Text(stringResource(R.string.profile_restore_dialog_message))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.profile_restore_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.profile_cancel))
            }
        }
    )
}
