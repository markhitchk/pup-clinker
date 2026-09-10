package com.harleytg.puppyclicker

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

private enum class PuppyExchangeTab(val title: String) {
    FRIENDS("Friends"),
    CONNECT("Connect"),
    GIFTS("Gifts"),
    TRADE("Trade"),
    HISTORY("History")
}

@Composable
internal fun PuppyExchangeScreen(
    state: V6GameState,
    vm: PuppyClickerV6ViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val ledger = remember { PuppyExchangeLedger(context) }
    val session = remember {
        PuppyExchangeSession(context) {
            ledger.snapshot().friends.asSequence()
                .filter { it.blocked }
                .map { it.playerId }
                .toSet()
        }
    }
    val connection by session.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(PuppyExchangeTab.FRIENDS) }

    DisposableEffect(session) {
        onDispose { session.disconnect() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Puppy Exchange", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text(
                    "Direct device-to-device gifting and trading.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onBack) { Text("Back") }
        }

        Spacer(Modifier.height(10.dp))
        ExchangeIdentityHeader(connection)
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            PuppyExchangeTab.entries.forEach { item ->
                FilterChip(
                    selected = tab == item,
                    onClick = { tab = item },
                    label = { Text(item.title) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        when (tab) {
            PuppyExchangeTab.FRIENDS -> ExchangeFriendsPanel(ledger, connection)
            PuppyExchangeTab.CONNECT -> ExchangeConnectPanel(session, connection)
            PuppyExchangeTab.GIFTS -> ExchangeGiftsPanel(state, connection)
            PuppyExchangeTab.TRADE -> ExchangeTradePanel(state, connection)
            PuppyExchangeTab.HISTORY -> ExchangeHistoryPanel(ledger)
        }
        Spacer(Modifier.height(26.dp))
    }
}

@Composable
private fun ExchangeIdentityHeader(connection: ExchangeConnectionState) {
    val context = LocalContext.current
    val username = remember { PuppyPlayerIdentity.username(context) }
    val friendCode = remember { PuppyPlayerIdentity.friendCode(context) }
    val connectionText = when (connection) {
        ExchangeConnectionState.Idle -> "Not connected"
        ExchangeConnectionState.CreatingOffer -> "Creating offer"
        ExchangeConnectionState.WaitingForAnswer -> "Waiting for answer"
        ExchangeConnectionState.ApplyingOffer -> "Applying offer"
        ExchangeConnectionState.Connecting -> "Connecting"
        is ExchangeConnectionState.Connected -> "Connected to ${connection.peer.username}"
        is ExchangeConnectionState.Failed -> connection.message
        ExchangeConnectionState.Closed -> "Disconnected"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(username, fontWeight = FontWeight.Black)
            Text(friendCode, style = MaterialTheme.typography.labelLarge)
            Text(connectionText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ExchangeFriendsPanel(
    ledger: PuppyExchangeLedger,
    connection: ExchangeConnectionState
) {
    var refresh by remember { mutableStateOf(0) }
    val friends = remember(refresh) { ledger.snapshot().friends.sortedBy { it.lastKnownUsername } }
    Text("Friends", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    Text(
        "Friend requests are live only. Both players must be online and directly connected.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(10.dp))

    val peer = (connection as? ExchangeConnectionState.Connected)?.peer
    if (peer != null) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Connected player", fontWeight = FontWeight.Black)
                Text(peer.username)
                Text(peer.friendCode, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Button(onClick = {
                        ledger.upsertFriend(
                            PuppyFriendRecord(
                                playerId = peer.playerId,
                                friendCode = peer.friendCode,
                                lastKnownUsername = peer.username,
                                addedAtMs = System.currentTimeMillis(),
                                blocked = false
                            )
                        )
                        refresh++
                    }) { Text("Add Friend") }
                    OutlinedButton(onClick = {
                        ledger.upsertFriend(
                            PuppyFriendRecord(
                                playerId = peer.playerId,
                                friendCode = peer.friendCode,
                                lastKnownUsername = peer.username,
                                addedAtMs = System.currentTimeMillis(),
                                blocked = true
                            )
                        )
                        refresh++
                    }) { Text("Block") }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    if (friends.isEmpty()) {
        Text("No saved friends yet.")
    } else {
        friends.forEach { friend ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(11.dp)) {
                    Text(friend.lastKnownUsername, fontWeight = FontWeight.Bold)
                    Text(friend.friendCode, style = MaterialTheme.typography.labelMedium)
                    Text(if (friend.blocked) "Blocked" else "Friend", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ExchangeConnectPanel(
    session: PuppyExchangeSession,
    connection: ExchangeConnectionState
) {
    val scope = rememberCoroutineScope()
    var friendCode by rememberSaveable { mutableStateOf("") }
    var offerCode by rememberSaveable { mutableStateOf("") }
    var answerCode by rememberSaveable { mutableStateOf("") }
    var generatedCode by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf<String?>(null) }

    Text("Manual Offer / Answer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    Text(
        "Friend Code verifies who you expect to connect to. Offer and Answer codes perform signaling; Puppy Clicker does not use a signaling server or QR codes.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(10.dp))

    OutlinedTextField(
        value = friendCode,
        onValueChange = { friendCode = it.uppercase().take(32) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Expected Friend Code") },
        singleLine = true
    )
    Spacer(Modifier.height(7.dp))
    Button(
        onClick = {
            scope.launch {
                runCatching { session.createOffer(friendCode) }
                    .onSuccess {
                        generatedCode = it
                        result = "Offer ready. Send this code to your friend."
                    }
                    .onFailure { result = it.message ?: "Unable to create offer." }
            }
        },
        enabled = PuppyPlayerIdentity.isValidFriendCode(friendCode),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Create Offer Code") }

    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = offerCode,
        onValueChange = { offerCode = it.take(524_288) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Paste Offer Code") },
        minLines = 2,
        maxLines = 5
    )
    Spacer(Modifier.height(7.dp))
    OutlinedButton(
        onClick = {
            scope.launch {
                runCatching { session.acceptOfferAndCreateAnswer(offerCode) }
                    .onSuccess {
                        generatedCode = it
                        result = "Answer ready. Send this code back to the other player."
                    }
                    .onFailure { result = it.message ?: "Unable to accept offer." }
            }
        },
        enabled = offerCode.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Create Answer Code") }

    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = answerCode,
        onValueChange = { answerCode = it.take(524_288) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Paste Answer Code") },
        minLines = 2,
        maxLines = 5
    )
    Spacer(Modifier.height(7.dp))
    OutlinedButton(
        onClick = {
            scope.launch {
                runCatching { session.applyAnswer(answerCode) }
                    .onSuccess { result = "Answer applied. Establishing direct connection…" }
                    .onFailure { result = it.message ?: "Unable to apply answer." }
            }
        },
        enabled = answerCode.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Apply Answer Code") }

    if (generatedCode.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = generatedCode,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            label = { Text("Generated Connection Code") },
            minLines = 2,
            maxLines = 6
        )
    }
    result?.let {
        Spacer(Modifier.height(7.dp))
        Text(it, style = MaterialTheme.typography.bodySmall)
    }
    if (connection is ExchangeConnectionState.Failed) {
        Spacer(Modifier.height(7.dp))
        Text(connection.message, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ExchangeGiftsPanel(state: V6GameState, connection: ExchangeConnectionState) {
    Text("Gifts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    val peer = (connection as? ExchangeConnectionState.Connected)?.peer
    if (peer == null) {
        Text("Connect to a verified friend before sending or accepting a gift.")
        return
    }
    Text("Recipient: ${peer.username} · ${peer.friendCode}")
    Spacer(Modifier.height(8.dp))
    Text("Current puppy: ${state.puppyName}")
    Text(
        "Eligible puppies are validated against both streamed transfer policy and local ownership before a gift can be committed.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ExchangeTradePanel(state: V6GameState, connection: ExchangeConnectionState) {
    Text("Trade", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    val peer = (connection as? ExchangeConnectionState.Connected)?.peer
    if (peer == null) {
        Text("Connect to a verified friend to build a trade.")
        return
    }
    Text("Trading with ${peer.username}")
    Text("Up to 5 puppies per side · editing either offer resets Ready.")
    Text(
        "Final swaps use a locked review and hold-to-confirm step. Interrupted commits move to Trade Recovery.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text("Active puppy: ${state.puppyName}", style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun ExchangeHistoryPanel(ledger: PuppyExchangeLedger) {
    val transactions = remember { ledger.snapshot().transactions.sortedByDescending { it.createdAtMs } }
    Text("History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    if (transactions.isEmpty()) {
        Text("No Puppy Exchange transactions on this device yet.")
        return
    }
    transactions.forEach { transaction ->
        Card(Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
            Column(Modifier.padding(11.dp)) {
                Text(transaction.type.name, fontWeight = FontWeight.Bold)
                Text(transaction.state.name)
                Text(transaction.transactionId, style = MaterialTheme.typography.labelSmall)
                if (transaction.state == ExchangeTransactionState.RECOVERY_REQUIRED) {
                    Text("Recovery required", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
