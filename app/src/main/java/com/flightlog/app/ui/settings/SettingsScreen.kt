package com.flightlog.app.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flightlog.app.data.backup.BackupMetadata
import com.google.android.gms.auth.api.signin.GoogleSignIn
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRestoreDialog by remember { mutableStateOf(false) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).result
            viewModel.onSignInResult(account)
        } else {
            viewModel.onSignInResult(null)
        }
    }

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
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            // ── Account Section ──────────────────────────────────────────────
            SectionHeader("Account")

            if (uiState.account != null) {
                ListItem(
                    headlineContent = { Text("Signed in as") },
                    supportingContent = { Text(uiState.account?.email ?: "") }
                )
                ListItem(
                    headlineContent = {
                        OutlinedButton(onClick = { viewModel.signOut() }) {
                            Text("Sign Out")
                        }
                    }
                )
            } else {
                ListItem(
                    headlineContent = { Text("Sign in with Google to enable cloud backup") },
                    supportingContent = { Text("Your backup is stored in a private app folder on your Google Drive") }
                )
                ListItem(
                    headlineContent = {
                        GoogleSignInButton(
                            onClick = { signInLauncher.launch(viewModel.getSignInIntent()) },
                            isLoading = uiState.isSigningIn
                        )
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Backup Section ───────────────────────────────────────────────
            SectionHeader("Backup")

            val metadata = uiState.backupMetadata
            if (metadata != null) {
                BackupInfoItem(metadata)
            } else {
                ListItem(
                    headlineContent = { Text("No backup yet") },
                    supportingContent = { Text("Back up your flights to Google Drive") }
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
                    enabled = uiState.account != null && !uiState.isBackingUp && !uiState.isRestoring,
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
                    Text("Back Up Now")
                }

                OutlinedButton(
                    onClick = { showRestoreDialog = true },
                    enabled = uiState.account != null && !uiState.isBackingUp && !uiState.isRestoring,
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
                    Text("Restore")
                }
            }

            if (uiState.account == null) {
                Text(
                    text = "Sign in to enable backup and restore",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── About Section ────────────────────────────────────────────────
            SectionHeader("About")

            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("1.0.0") }
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
        headlineContent = { Text("Last backup: $dateStr") },
        supportingContent = { Text("${metadata.flightCount} flights, $sizeStr") }
    )
}

@Composable
private fun GoogleSignInButton(
    onClick: () -> Unit,
    isLoading: Boolean
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !isLoading,
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline,
            shape = RoundedCornerShape(4.dp)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = "Sign in with Google",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RestoreConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore from Drive") },
        text = {
            Text(
                "This will import flights from your backup. " +
                "Flights that already exist in your logbook will be skipped. " +
                "No existing data will be deleted."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Restore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
