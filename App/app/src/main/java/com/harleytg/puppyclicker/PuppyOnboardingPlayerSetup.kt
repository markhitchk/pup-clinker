package com.harleytg.puppyclicker

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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

    var username by rememberSaveable {
        mutableStateOf(
            PuppyPlayerIdentity.username(context)
                .takeUnless { it == "localplayer" }
                .orEmpty()
        )
    }
    var usernameModerationMessage by rememberSaveable { mutableStateOf<String?>(null) }

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
        title = "Player Setup",
        canGoBack = true,
        primaryLabel = null,
        onBack = onBack
    ) {
        Text(
            "How do you want to play?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black
        )
        Text(
            "Choose one setup method. You can connect Discord or manage saves later in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(14.dp))

        PlayerSetupMethodRow(
            title = "Local Profile",
            detail = "Create a username on this device",
            selected = session.playerSetupMethod == PuppyPlayerSetupMethod.LOCAL,
            onClick = {
                onSessionChange(session.copy(playerSetupMethod = PuppyPlayerSetupMethod.LOCAL))
            }
        )
        Spacer(Modifier.height(7.dp))
        PlayerSetupMethodRow(
            title = "Discord",
            detail = "Verify with your Discord account",
            selected = session.playerSetupMethod == PuppyPlayerSetupMethod.DISCORD,
            onClick = {
                onSessionChange(session.copy(playerSetupMethod = PuppyPlayerSetupMethod.DISCORD))
            }
        )
        Spacer(Modifier.height(7.dp))
        PlayerSetupMethodRow(
            title = "Import Save",
            detail = "Restore an existing .pupsave",
            selected = session.playerSetupMethod == PuppyPlayerSetupMethod.IMPORT_SAVE,
            onClick = {
                onSessionChange(session.copy(playerSetupMethod = PuppyPlayerSetupMethod.IMPORT_SAVE))
            }
        )

        Spacer(Modifier.height(18.dp))

        when (session.playerSetupMethod) {
            PuppyPlayerSetupMethod.LOCAL -> {
                Text("Local Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Stored only on this device. No online account is required.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.take(24) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Player username") },
                    supportingText = { Text("Letters, numbers, _, - and .") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val moderationIssue = PuppyPlayerIdentity.usernameModerationIssue(username)
                        if (moderationIssue != null) {
                            usernameModerationMessage = moderationIssue
                        } else {
                            username = PuppyPlayerIdentity.setUsername(context, username)
                            onComplete()
                        }
                    },
                    enabled = localUsernameSubmissionEnabled(username),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create Local Profile", fontWeight = FontWeight.Bold)
                }
            }

            PuppyPlayerSetupMethod.DISCORD -> {
                Text("Discord", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Optional verification using only Discord's identify permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_discord),
                            contentDescription = "Discord",
                            modifier = Modifier.size(30.dp),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp)
                        ) {
                            Text(
                                account?.displayName ?: "Discord account",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                when {
                                    account != null -> "@${account.username}"
                                    discord.phase == DiscordSignupPhase.EXCHANGING -> "Verifying account…"
                                    discord.phase == DiscordSignupPhase.AUTHORIZING -> "Finish authorization in Discord"
                                    else -> "Not connected"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            if (account != null) "Connected" else "Optional",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (account != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
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
                    Button(
                        onClick = {
                            val preferred = account.username
                            val localUsername = if (PuppyPlayerIdentity.isUsernameAllowed(preferred)) {
                                preferred
                            } else {
                                "player_" + account.id.takeLast(8)
                            }
                            PuppyPlayerIdentity.setUsername(context, localUsername)
                            onComplete()
                        },
                        enabled = !discordBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Continue with Discord", fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = { DiscordSignupAuth.startSignup(context) },
                        enabled = !discordBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use a Different Discord Account")
                    }
                }

                discord.message?.let { message ->
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
            }

            PuppyPlayerSetupMethod.IMPORT_SAVE -> {
                Text("Import Save", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Restore your progress without replacing this device's Player ID or Friend Code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        importLauncher.launch(
                            arrayOf("application/octet-stream", "application/json", "*/*")
                        )
                    },
                    enabled = !checkingSave && !importingSave,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (selectedSaveUri == null) "Choose .pupsave" else "Choose Different Save"
                    )
                }

                selectedSaveUri?.let { uri ->
                    Text(
                        uri.lastPathSegment?.substringAfterLast('/') ?: "Selected save",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (checkingSave) {
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
                    Spacer(Modifier.height(8.dp))
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
                                    "Enter the password if this is an encrypted save."
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

                if (selectedSaveUri != null && !checkingSave) {
                    Spacer(Modifier.height(8.dp))
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
                                    vm.reloadImportedSave()
                                    onSessionChange(
                                        session.copy(
                                            playerSetupMethod = PuppyPlayerSetupMethod.IMPORT_SAVE,
                                            importedSave = true
                                        )
                                    )
                                    onComplete()
                                }
                            }
                        },
                        enabled = !importingSave &&
                            (
                                passwordRequirement == PuppySavePasswordRequirement.NOT_REQUIRED ||
                                    password.length >= 8
                                ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (importingSave) "Importing…" else "Import Save",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                importMessage?.let { message ->
                    Spacer(Modifier.height(6.dp))
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
            }
        }
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
private fun PlayerSetupMethodRow(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                if (selected) "Selected" else "Choose",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
