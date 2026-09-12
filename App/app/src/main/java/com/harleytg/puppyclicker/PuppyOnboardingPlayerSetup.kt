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
    val discordBusy = discord.phase == DiscordSignupPhase.AUTHORIZING ||
        discord.phase == DiscordSignupPhase.EXCHANGING
    val scope = rememberCoroutineScope()
    val flags by PuppyFeatureFlags.flags.collectAsStateWithLifecycle()
    val accountFlag = flags["puppy_clicker_account"] ?: PuppyFeatureFlags.flag("puppy_clicker_account")
    val discordFlag = flags["discord_linking"] ?: PuppyFeatureFlags.flag("discord_linking")
    val restoreFlag = flags["save_restore"] ?: PuppyFeatureFlags.flag("save_restore")

    var username by rememberSaveable {
        mutableStateOf(
            PuppyPlayerIdentity.username(context)
                .takeUnless { it == "localplayer" }
                .orEmpty()
        )
    }
    var usernameModerationMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var backupPassword by rememberSaveable {
        mutableStateOf(PuppyBackupPasswordStore.get(context).orEmpty())
    }
    var backupPasswordConfirm by rememberSaveable {
        mutableStateOf(PuppyBackupPasswordStore.get(context).orEmpty())
    }
    var backupPasswordVisible by rememberSaveable { mutableStateOf(false) }
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

    val profileSourceLabel = when (session.playerSetupMethod) {
        PuppyPlayerSetupMethod.LOCAL -> "Local profile"
        PuppyPlayerSetupMethod.DISCORD -> "Discord-linked profile"
        PuppyPlayerSetupMethod.IMPORT_SAVE -> "Restored save"
    }

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
            "One account setup for local now and online later.",
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
            Column(Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SetupBadge("PC")
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                    ) {
                        Text("Puppy Clicker Account Credentials", fontWeight = FontWeight.Black)
                        Text(
                            profileSourceLabel + " · local now · online-ready",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.take(24) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Username") },
                    singleLine = true
                )

                Spacer(Modifier.height(5.dp))
                OutlinedTextField(
                    value = backupPassword,
                    onValueChange = { backupPassword = it.take(128) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { backupPasswordVisible = !backupPasswordVisible }) {
                            Text(if (backupPasswordVisible) "Hide" else "View")
                        }
                    },
                    visualTransformation = if (backupPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    }
                )
                Spacer(Modifier.height(5.dp))
                OutlinedTextField(
                    value = backupPasswordConfirm,
                    onValueChange = { backupPasswordConfirm = it.take(128) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirm password") },
                    singleLine = true,
                    visualTransformation = if (backupPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    }
                )

                account?.let {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "Discord linked: @" + it.username,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Button(
                    onClick = {
                        val moderationIssue = PuppyPlayerIdentity.usernameModerationIssue(username)
                        if (moderationIssue != null) {
                            usernameModerationMessage = moderationIssue
                        } else if (backupPassword.length < 8) {
                            usernameModerationMessage = "Your password must be at least 8 characters."
                        } else if (backupPassword != backupPasswordConfirm) {
                            usernameModerationMessage = "Your passwords do not match."
                        } else {
                            username = PuppyPlayerIdentity.setUsername(context, username)
                            val backupSaved = runCatching {
                                PuppyBackupPasswordStore.set(context, backupPassword)
                            }.isSuccess
                            if (!backupSaved) {
                                usernameModerationMessage =
                                    "Unable to protect the backup password on this device."
                            } else {
                                onSessionChange(
                                    session.copy(playerSetupMethod = PuppyPlayerSetupMethod.LOCAL)
                                )
                                onComplete()
                            }
                        }
                    },
                    enabled = localUsernameSubmissionEnabled(username) &&
                        backupPassword.length >= 8 &&
                        backupPassword == backupPasswordConfirm,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Account & Continue", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (accountFlag.visible) {
            Spacer(Modifier.height(6.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            accountFlag.label.ifBlank { "Puppy Clicker Online Account" },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            if (accountFlag.isAvailable()) {
                                "Uses the same username and password above."
                            } else {
                                "Coming soon · the same credentials will be used."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        accountFlag.statusLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (discordFlag.visible) {
            Spacer(Modifier.height(6.dp))
            Text("DISCORD ACCOUNT", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(3.dp))
            OnboardingOptionCard(
                title = if (account == null) "Connect Discord" else "Discord Connected",
                detail = if (account == null) {
                    "Optional identity link."
                } else {
                    "@" + account.username + " linked."
                },
                action = if (!discordFlag.isAvailable()) {
                    discordFlag.statusLabel()
                } else if (account == null) {
                    "Connect"
                } else {
                    "Manage"
                },
                enabled = discordFlag.isAvailable(),
                onClick = { discordSheetOpen = true },
                leading = {
                    Image(
                        painter = painterResource(R.drawable.ic_discord),
                        contentDescription = "Discord",
                        modifier = Modifier.size(30.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                    )
                }
            )
        }

        if (restoreFlag.visible) {
            Spacer(Modifier.height(6.dp))
            Text("SAVE RESTORE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(3.dp))
            OnboardingOptionCard(
                title = "Restore a Save",
                detail = "Import an existing .pupsave.",
                action = if (restoreFlag.isAvailable()) "Import" else restoreFlag.statusLabel(),
                enabled = restoreFlag.isAvailable(),
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
                            "Optional account connection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Puppy Clicker only uses Discord's identify permission. Your Player ID, Friend Code, progress, and save remain local.",
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
                } else {
                    val discordUsernameAllowed =
                        PuppyPlayerIdentity.isUsernameAllowed(account.username)

                    Button(
                        onClick = {
                            username = account.username
                            onSessionChange(
                                session.copy(playerSetupMethod = PuppyPlayerSetupMethod.DISCORD)
                            )
                            discordSheetOpen = false
                        },
                        enabled = discordUsernameAllowed && !discordBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use @" + account.username + " as Player Username")
                    }

                    if (!discordUsernameAllowed) {
                        Text(
                            "This Discord username cannot be used as a Puppy Clicker username. You can still keep the Discord account linked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    OutlinedButton(
                        onClick = { DiscordSignupAuth.startSignup(context) },
                        enabled = !discordBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use a Different Discord Account")
                    }

                    TextButton(
                        onClick = { discordSheetOpen = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done")
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
                        "Restoring progress does not replace this device's Player ID or Friend Code.",
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

    if (usernameModerationMessage != null) {
        AlertDialog(
            onDismissRequest = { usernameModerationMessage = null },
            title = {
                Text("Account Setup Issue", fontWeight = FontWeight.Black)
            },
            text = {
                Text(
                    usernameModerationMessage
                        ?: "That username isn't allowed. Choose another username."
                )
            },
            confirmButton = {
                TextButton(onClick = { usernameModerationMessage = null }) {
                    Text("Choose Another")
                }
            }
        )
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
