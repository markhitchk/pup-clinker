package com.harleytg.puppyclicker

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PuppyOnboardingPlayerSetup(
    vm: PuppyClickerV6ViewModel,
    session: PuppyOnboardingSessionState,
    onSessionChange: (PuppyOnboardingSessionState) -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val discord by DiscordSignupAuth.observe(context).collectAsStateWithLifecycle()
    val account = discord.account
    val pending = discord.pendingAccount
    var showCodeEntry by rememberSaveable { mutableStateOf(false) }
    val discordBusy = discord.phase == DiscordSignupPhase.AUTHORIZING ||
        discord.phase == DiscordSignupPhase.EXCHANGING
    LaunchedEffect(discord.phase) {
        if (discord.phase == DiscordSignupPhase.CODE_PENDING) showCodeEntry = true
    }
    val scope = rememberCoroutineScope()
    val flags by PuppyFeatureFlags.flags.collectAsStateWithLifecycle()
    val discordFlag = flags["discord_linking"] ?: PuppyFeatureFlags.flag("discord_linking")
    val restoreFlag = flags["save_restore"] ?: PuppyFeatureFlags.flag("save_restore")

    var discordSheetOpen by rememberSaveable { mutableStateOf(false) }
    var importSheetOpen by rememberSaveable { mutableStateOf(false) }

    var selectedSaveUri by remember { mutableStateOf<Uri?>(null) }
    var passwordRequirement by remember {
        mutableStateOf(PuppySavePasswordRequirement.UNKNOWN)
    }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var checkingSave by remember { mutableStateOf(false) }
    var importingSave by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedSaveUri = uri
            importMessage = null
            checkingSave = true
            scope.launch {
                passwordRequirement = withContext(Dispatchers.IO) {
                    GameSaveTransfer.passwordRequirement(context, uri)
                }
                checkingSave = false
            }
        }
    }

    PuppyOnboardingShell(
        step = PuppyOnboardingStep.PLAYER_SETUP,
        title = "Set Up Your Account",
        canGoBack = true,
        primaryLabel = null,
        onBack = onBack,
        preferViewportFit = true
    ) {
        Text(
            "T0 Pup Accounts are passwordless. Discord signs you in; PupEye binds one active device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(6.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_discord),
                        contentDescription = "Discord",
                        modifier = Modifier.size(34.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                    ) {
                        Text("Pup Account · T0", fontWeight = FontWeight.Black)
                        Text(
                            if (account == null) {
                                "Passwordless sign-in with Discord"
                            } else {
                                account.displayName + " · @" + account.username
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            if (account == null) "SIGN IN" else "CONNECTED",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    if (account == null) {
                        "Discord provides your username and identity. Puppy Clicker does not create or store a separate account password."
                    } else {
                        "Your Pup Account is backed by Supabase cloud progression and protected by this device's PupEye key."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(10.dp))

                if (account == null) {
                    Button(
                        onClick = { discordSheetOpen = true },
                        enabled = discordFlag.isAvailable() && !discordBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (discordBusy) "Waiting for Discord…" else "Sign in with Discord",
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            val preferredUsername = if (
                                PuppyPlayerIdentity.isUsernameAllowed(account.username)
                            ) {
                                account.username
                            } else {
                                "pup" + account.id.takeLast(8)
                            }
                            PuppyPlayerIdentity.setUsername(context, preferredUsername)
                            onSessionChange(
                                session.copy(playerSetupMethod = PuppyPlayerSetupMethod.DISCORD)
                            )
                            PupAccountCloudSave.activate(context)
                            onComplete()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Continue with @" + account.username, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(4.dp))

                    TextButton(
                        onClick = { discordSheetOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Manage Discord sign-in")
                    }
                }
            }
        }

        if (restoreFlag.visible) {
            Spacer(Modifier.height(6.dp))
            Text("SAVE RESTORE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(3.dp))
            OnboardingOptionCard(
                title = "Restore a Save",
                detail = if (account == null) {
                    "Sign in to a Pup Account before importing legacy progress."
                } else {
                    "Import an existing .pupsave, then upload it to your Pup Account."
                },
                action = if (account == null) {
                    "Sign in first"
                } else if (restoreFlag.isAvailable()) {
                    "Import"
                } else {
                    restoreFlag.statusLabel()
                },
                enabled = account != null && restoreFlag.isAvailable(),
                onClick = { importSheetOpen = true },
                leading = { SetupBadge("SAVE") }
            )
        }

        Spacer(Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StreamedPupEyeBranding(
                    modifier = Modifier.size(30.dp),
                    contentDescription = "PupEye protection"
                )
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Protected by PupEye",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Save integrity + fair-play protection.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (discordSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { discordSheetOpen = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_discord),
                        contentDescription = "Discord",
                        modifier = Modifier.size(36.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                    )
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(
                            "Connect Discord",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "Passwordless Pup Account sign-in",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Discord OAuth proves your Pup Account identity without a Puppy Clicker password. Your username and display name come from Discord; Supabase stores the account and cloud progression. PupEye still allows only one active device.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            if (account == null) "Discord account" else "Account connected",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            when {
                                account != null -> account.displayName + " · @" + account.username
                                discord.phase == DiscordSignupPhase.EXCHANGING -> "Verifying account…"
                                discord.phase == DiscordSignupPhase.AUTHORIZING -> "Finish authorization in Discord"
                                else -> "Not connected"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                discord.message?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (discord.phase == DiscordSignupPhase.ERROR) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (showCodeEntry) {
                    PuppyDiscordCodeEntryDialog(
                        onDismiss = { showCodeEntry = false },
                        onVerified = { snapshot ->
                            val linked = account ?: pending
                            if (linked != null) {
                                DiscordSignupAuth.completeVerifiedLink(context, linked, snapshot)
                            }
                            showCodeEntry = false
                        }
                    )
                }

                if (account == null) {
                    Button(
                        onClick = { DiscordSignupAuth.startSignup(context) },
                        enabled = !discordBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (discordBusy) "Waiting for Discord…" else "Connect Discord",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (pending != null || discord.phase == DiscordSignupPhase.CODE_PENDING) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showCodeEntry = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Enter code") }
                    }
                } else {
                    Button(
                        onClick = { discordSheetOpen = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use this Pup Account", fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(6.dp))

                    OutlinedButton(
                        onClick = { DiscordSignupAuth.startSignup(context) },
                        enabled = !discordBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use a Different Discord Account")
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (importSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = {
                if (!importingSave) importSheetOpen = false
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    "Restore Puppy Clicker Save",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Select a .pupsave file to restore your progress.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !checkingSave && !importingSave) {
                            importLauncher.launch(
                                arrayOf("application/octet-stream", "application/json", "*/*")
                            )
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SetupBadge("SAVE")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (selectedSaveUri == null) {
                                "Choose .pupsave file"
                            } else {
                                "Choose a different save"
                            },
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            selectedSaveUri?.lastPathSegment?.substringAfterLast('/')
                                ?: "Tap to browse your device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (checkingSave) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Checking save format…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (
                    selectedSaveUri != null &&
                    !checkingSave &&
                    passwordRequirement != PuppySavePasswordRequirement.NOT_REQUIRED
                ) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it.take(128) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Backup password") },
                        supportingText = {
                            Text(
                                if (passwordRequirement == PuppySavePasswordRequirement.REQUIRED) {
                                    "Required for encrypted v3 saves."
                                } else {
                                    "Enter the password if this save is encrypted."
                                }
                            )
                        },
                        singleLine = true,
                        trailingIcon = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(if (passwordVisible) "Hide" else "View")
                            }
                        },
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        }
                    )
                }

                importMessage?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (message.startsWith("Import failed", ignoreCase = true)) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        val uri = selectedSaveUri ?: return@Button
                        importingSave = true
                        importMessage = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                GameSaveTransfer.import(context, uri, password)
                            }
                            importingSave = false
                            importMessage = result.message
                            if (result.success) {
                                if (password.length >= 8) {
                                    runCatching { PuppyBackupPasswordStore.set(context, password) }
                                }
                                vm.reloadImportedSave()
                                PupAccountCloudSave.queueSync(context, reason = "legacy-import")
                                onSessionChange(
                                    session.copy(
                                        playerSetupMethod = PuppyPlayerSetupMethod.IMPORT_SAVE,
                                        importedSave = true
                                    )
                                )
                                importSheetOpen = false
                                onComplete()
                            }
                        }
                    },
                    enabled = selectedSaveUri != null &&
                        !checkingSave &&
                        !importingSave &&
                        (
                            passwordRequirement == PuppySavePasswordRequirement.NOT_REQUIRED ||
                                password.length >= 8
                            ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (importingSave) "Restoring…" else "Restore Save",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        "A fresh authenticated backup can securely move your existing Player ID, Friend Code, and progress to this device.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = { importSheetOpen = false },
                    enabled = !importingSave,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }

}

@Composable
private fun OnboardingOptionCard(
    title: String,
    detail: String,
    action: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    leading: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            leading()
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                action,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun SetupBadge(text: String) {
    Surface(
        modifier = Modifier.size(34.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black
            )
        }
    }
}
