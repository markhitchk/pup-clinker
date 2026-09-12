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
import androidx.compose.runtime.produceState
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
    val featureFlags by produceState(
        initialValue = PuppyFeatureFlags.cached(context),
        key1 = context
    ) {
        value = PuppyFeatureFlags.refresh(context)
    }
    val onlineAccountFlag = featureFlags.flag("puppy_clicker_account")
    val discordLinkingFlag = featureFlags.flag("discord_linking")
    val saveRestoreFlag = featureFlags.flag("save_restore")

    var onlineUsername by rememberSaveable { mutableStateOf("") }
    var onlinePassword by rememberSaveable { mutableStateOf("") }
    var onlinePasswordVisible by rememberSaveable { mutableStateOf(false) }

    var username by rememberSaveable {
        mutableStateOf(
            PuppyPlayerIdentity.username(context)
                .takeUnless { it == "localplayer" }
                .orEmpty()
        )
    }
    var usernameModerationMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var backupPasswordError by rememberSaveable { mutableStateOf<String?>(null) }
    var backupPassword by rememberSaveable {
        mutableStateOf(PuppyLocalBackupPassword.load(context).orEmpty())
    }
    var backupPasswordConfirm by rememberSaveable {
        mutableStateOf(PuppyLocalBackupPassword.load(context).orEmpty())
    }
    var backupPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var backupPasswordConfirmVisible by rememberSaveable { mutableStateOf(false) }
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
        PuppyPlayerSetupMethod.LOCAL -> "Puppy Clicker Local Account"
        PuppyPlayerSetupMethod.ONLINE_ACCOUNT -> "Puppy Clicker Account"
        PuppyPlayerSetupMethod.DISCORD -> "Puppy Clicker Local Account · Discord linked"
        PuppyPlayerSetupMethod.IMPORT_SAVE -> "Restored Puppy Clicker Local Account"
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
        title = "Create Your Player",
        canGoBack = true,
        primaryLabel = null,
        onBack = onBack
    ) {
        Text(
            "Choose your Puppy Clicker account.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black
        )
        Text(
            "Puppy Clicker Local Account works entirely on this device. The upcoming Puppy Clicker Account adds online username/password sign-in with Discord authentication.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(14.dp))

        if (onlineAccountFlag.visible) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SetupBadge("PC+")
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp)
                        ) {
                            Text("Puppy Clicker Account", fontWeight = FontWeight.Black)
                            Text(
                                "Online account · username/password + Discord authentication",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                onlineAccountFlag.badgeText(),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = onlineUsername,
                        onValueChange = { onlineUsername = it.take(24) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Account username") },
                        supportingText = {
                            Text("Your Puppy Clicker Account username.")
                        },
                        enabled = false,
                        singleLine = true
                    )

                    Spacer(Modifier.height(6.dp))

                    OutlinedTextField(
                        value = onlinePassword,
                        onValueChange = { onlinePassword = it.take(128) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Account password") },
                        supportingText = {
                            Text("Password sign-in for your online Puppy Clicker Account.")
                        },
                        enabled = false,
                        singleLine = true,
                        trailingIcon = {
                            TextButton(
                                onClick = {
                                    onlinePasswordVisible = !onlinePasswordVisible
                                },
                                enabled = false
                            ) {
                                Text(if (onlinePasswordVisible) "Hide" else "View")
                            }
                        },
                        visualTransformation = if (onlinePasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        }
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_discord),
                            contentDescription = "Discord authentication",
                            modifier = Modifier.size(26.dp),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                        ) {
                            Text("Discord authentication", fontWeight = FontWeight.Bold)
                            Text(
                                if (account == null) {
                                    "A Discord account will be linked during online account setup."
                                } else {
                                    "Connected: @" + account.username
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "This account service is not active in this build yet. The remote flag controls its visibility and release-date label; account creation stays disabled until the online backend is shipped.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(14.dp)) {
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
                        Text("Puppy Clicker Local Account", fontWeight = FontWeight.Black)
                        Text(
                            profileSourceLabel + " · stored only on this device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.take(24) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Player username") },
                    supportingText = {
                        Text("Letters, numbers, _, - and . · up to 24 characters")
                    },
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = backupPassword,
                    onValueChange = {
                        backupPassword = it.take(128)
                        backupPasswordError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Backup password") },
                    supportingText = {
                        Text("8+ characters · used to encrypt exported .pupsave backups")
                    },
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

                Spacer(Modifier.height(6.dp))

                val backupPasswordsMatch =
                    backupPasswordConfirm.isBlank() || backupPassword == backupPasswordConfirm
                OutlinedTextField(
                    value = backupPasswordConfirm,
                    onValueChange = {
                        backupPasswordConfirm = it.take(128)
                        backupPasswordError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirm backup password") },
                    supportingText = {
                        Text(
                            if (backupPasswordsMatch) {
                                "This becomes your Puppy Clicker Local Account backup password."
                            } else {
                                "Passwords do not match."
                            }
                        )
                    },
                    isError = !backupPasswordsMatch,
                    singleLine = true,
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                backupPasswordConfirmVisible = !backupPasswordConfirmVisible
                            }
                        ) {
                            Text(if (backupPasswordConfirmVisible) "Hide" else "View")
                        }
                    },
                    visualTransformation = if (backupPasswordConfirmVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    }
                )

                Text(
                    "Stored device-bound with Android Keystore. The password itself is never written into an exported save.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        val moderationIssue = PuppyPlayerIdentity.usernameModerationIssue(username)
                        when {
                            moderationIssue != null -> {
                                usernameModerationMessage = moderationIssue
                            }
                            backupPassword.length < 8 -> {
                                backupPasswordError =
                                    "Your backup password must be at least 8 characters."
                            }
                            backupPassword != backupPasswordConfirm -> {
                                backupPasswordError = "Your backup passwords do not match."
                            }
                            !PuppyLocalBackupPassword.save(context, backupPassword) -> {
                                backupPasswordError =
                                    "Puppy Clicker could not secure the backup password on this device."
                            }
                            else -> {
                                username = PuppyPlayerIdentity.setUsername(context, username)
                                onComplete()
                            }
                        }
                    },
                    enabled = localUsernameSubmissionEnabled(username) &&
                        backupPassword.length >= 8 &&
                        backupPassword == backupPasswordConfirm,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create Local Account & Continue", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text(
                "More options",
                modifier = Modifier.padding(horizontal = 10.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
        Spacer(Modifier.height(10.dp))

        if (discordLinkingFlag.visible) {
            OnboardingOptionCard(
                title = if (account == null) "Connect Discord" else "Discord Connected",
                detail = if (discordLinkingFlag.isAvailable()) {
                    if (account == null) {
                        "Link Discord to your Puppy Clicker Local Account for identity and community features."
                    } else {
                        "@" + account.username + " is linked. Tap to manage."
                    }
                } else {
                    "Discord linking is currently unavailable."
                },
                action = if (discordLinkingFlag.isAvailable()) {
                    if (account == null) "Connect" else "Manage"
                } else {
                    discordLinkingFlag.badgeText()
                },
                enabled = discordLinkingFlag.isAvailable(),
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
            Spacer(Modifier.height(8.dp))
        }

        if (saveRestoreFlag.visible) {
            OnboardingOptionCard(
                title = "Restore a Save",
                detail = if (saveRestoreFlag.isAvailable()) {
                    "Already played before? Import an existing .pupsave file."
                } else {
                    "Save restore is currently unavailable."
                },
                action = if (saveRestoreFlag.isAvailable()) {
                    "Import"
                } else {
                    saveRestoreFlag.badgeText()
                },
                enabled = saveRestoreFlag.isAvailable(),
                onClick = { importSheetOpen = true },
                leading = { SetupBadge("SAVE") }
            )
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
                    "For Puppy Clicker Local Account, Discord is optional and only the identify permission is requested. Your Player ID, Friend Code, progress, and save remain local.",
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
                                    PuppyLocalBackupPassword.save(context, password)
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

    if (backupPasswordError != null) {
        AlertDialog(
            onDismissRequest = { backupPasswordError = null },
            title = {
                Text("Backup Password", fontWeight = FontWeight.Black)
            },
            text = {
                Text(backupPasswordError ?: "Check your backup password and try again.")
            },
            confirmButton = {
                TextButton(onClick = { backupPasswordError = null }) {
                    Text("OK")
                }
            }
        )
    }

    if (usernameModerationMessage != null) {
        AlertDialog(
            onDismissRequest = { usernameModerationMessage = null },
            title = {
                Text("Username Not Allowed", fontWeight = FontWeight.Black)
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
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
        modifier = Modifier.size(42.dp),
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
