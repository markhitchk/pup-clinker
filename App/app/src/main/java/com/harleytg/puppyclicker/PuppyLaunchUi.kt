package com.harleytg.puppyclicker

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun PuppyWelcomeTitleScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
                        )
                    )
                )
                .padding(horizontal = 26.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.source_logo),
                contentDescription = "Puppy Clicker logo",
                modifier = Modifier.size(210.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Puppy Clicker",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Tap. Care. Collect. Repeat.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StreamedPupEyeBranding(
                        modifier = Modifier.size(34.dp),
                        contentDescription = "PupEye anti-cheat logo"
                    )
                    Spacer(Modifier.size(9.dp))
                    Column {
                        Text("Protected by PupEye Anti-Cheat", fontWeight = FontWeight.Bold)
                        Text(
                            "Fair-play and encrypted save protection.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { PuppyLinks.openDiscord(context) },
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_discord),
                            contentDescription = "Discord",
                            modifier = Modifier.padding(8.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.size(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Join Our Discord Server", fontWeight = FontWeight.Black)
                        Text(
                            PuppyLinks.DISCORD_INVITE,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Play Puppy Clicker", fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(4.dp))
            PuppyLegalLinks(
                modifier = Modifier.fillMaxWidth(),
                acknowledgementText = "By continuing, you agree to the Terms of Use and acknowledge the Privacy Policy."
            )
            Spacer(Modifier.height(8.dp))
            PuppyDevelopmentNotice()
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.harleys_studios_icon),
                    contentDescription = "Harley's Studios",
                    modifier = Modifier.size(24.dp),
                    contentScale = ContentScale.Fit
                )
                Text(
                    "Harley's Studios",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun PuppySignupScreen(onContinueAsGuest: () -> Unit) {
    val context = LocalContext.current
    var username by rememberSaveable {
        mutableStateOf(
            PuppyPlayerIdentity.username(context)
                .takeUnless { it == "localplayer" }
                .orEmpty()
        )
    }
    val normalizedUsername = PuppyPlayerIdentity.normalizeUsername(username)

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.source_logo),
                    contentDescription = "Puppy Clicker logo",
                    modifier = Modifier.size(118.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Sign up for Puppy Clicker",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Create a Puppy Clicker Local Account for this device. Puppy Clicker Account online sign-in is coming soon, with username/password and Discord authentication.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = PuppyPlayerIdentity.normalizeUsername(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Player username") },
                    supportingText = {
                        Text("Saved as lowercase and embedded inside encrypted save metadata.")
                    },
                    singleLine = true
                )

                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { PuppyLinks.openDiscord(context) },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_discord),
                        contentDescription = null,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Join Discord Community")
                }
                Text(
                    PuppyLinks.DISCORD_INVITE,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(9.dp))
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("🌐  Website Login · Coming Soon")
                }

                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        PuppyPlayerIdentity.setUsername(context, normalizedUsername)
                        onContinueAsGuest()
                    },
                    enabled = normalizedUsername.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Continue with Puppy Clicker Local Account", fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(4.dp))
                PuppyLegalLinks(
                    modifier = Modifier.fillMaxWidth(),
                    acknowledgementText = "Creating a Puppy Clicker Local Account means you agree to the Terms of Use and acknowledge the Privacy Policy."
                )
                Spacer(Modifier.height(8.dp))
                PuppyDevelopmentNotice()
                Spacer(Modifier.height(8.dp))
                Text(
                    "PupEye does not store IMEI, serial number, Android ID, phone number, or account tokens in save metadata.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
