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
import androidx.compose.runtime.LaunchedEffect
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
import org.json.JSONArray
import org.json.JSONObject

private enum class PuppyExchangeTab(val title: String) {
    FRIENDS("Friends"),
    CONNECT("Connect"),
    GIFTS("Gifts"),
    TRADE("Trade"),
    HISTORY("History")
}

private data class LiveGiftOffer(
    val offerId: String,
    val puppyId: String
)

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
    val realtime by session.realtime.collectAsStateWithLifecycle()
    val assets by DynamicPuppyRoster.assets.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(PuppyExchangeTab.FRIENDS) }
    var incomingFriendRequest by remember { mutableStateOf(false) }
    var friendStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var incomingGift by remember { mutableStateOf<LiveGiftOffer?>(null) }
    var giftStatus by rememberSaveable { mutableStateOf<String?>(null) }

    var localTradeIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var remoteTradeIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var localReady by remember { mutableStateOf(false) }
    var remoteReady by remember { mutableStateOf(false) }
    var localCommit by remember { mutableStateOf(false) }
    var remoteCommit by remember { mutableStateOf(false) }
    var tradeCompleted by remember { mutableStateOf(false) }
    var tradeStatus by rememberSaveable { mutableStateOf<String?>(null) }

    val peer = (connection as? ExchangeConnectionState.Connected)?.peer

    DisposableEffect(session) {
        onDispose { session.disconnect() }
    }

    LaunchedEffect(connection) {
        if (connection !is ExchangeConnectionState.Connected) {
            incomingFriendRequest = false
            incomingGift = null
            localTradeIds = emptyList()
            remoteTradeIds = emptyList()
            localReady = false
            remoteReady = false
            localCommit = false
            remoteCommit = false
            tradeCompleted = false
        }
    }

    LaunchedEffect(session, peer?.playerId) {
        session.messages.collect { message ->
            when (message.type) {
                "friend.request" -> {
                    incomingFriendRequest = true
                    friendStatus = "Incoming friend request."
                }
                "friend.response" -> {
                    val accepted = message.payload.optBoolean("accepted", false)
                    if (accepted && peer != null) {
                        ledger.upsertFriend(
                            PuppyFriendRecord(
                                playerId = peer.playerId,
                                friendCode = peer.friendCode,
                                lastKnownUsername = peer.username,
                                addedAtMs = System.currentTimeMillis(),
                                blocked = false
                            )
                        )
                        friendStatus = "${peer.username} accepted your friend request."
                    } else {
                        friendStatus = "Friend request declined."
                    }
                }
                "gift.offer" -> {
                    val offerId = message.payload.optString("offerId")
                    val puppyId = message.payload.optString("puppyId")
                    if (offerId.startsWith("XT-") && DynamicPuppyRoster.asset(puppyId) != null) {
                        incomingGift = LiveGiftOffer(offerId, puppyId)
                        giftStatus = "Incoming gift from ${peer?.username ?: "connected player"}."
                        tab = PuppyExchangeTab.GIFTS
                    }
                }
                "gift.response" -> {
                    val accepted = message.payload.optBoolean("accepted", false)
                    val puppyId = message.payload.optString("puppyId")
                    val offerId = message.payload.optString("offerId")
                    giftStatus = if (accepted) "Gift accepted." else "Gift declined."
                    if (accepted && peer != null && offerId.startsWith("XT-")) {
                        recordGiftHistory(
                            ledger = ledger,
                            transactionId = offerId,
                            localPlayerId = PuppyPlayerIdentity.playerId(context),
                            remotePlayerId = peer.playerId,
                            puppyId = puppyId
                        )
                    }
                }
                "trade.offer" -> {
                    remoteTradeIds = message.payload.optJSONArray("puppyIds").stringList()
                        .filter { DynamicPuppyRoster.asset(it) != null }
                        .distinct()
                        .take(PuppyExchangeLedger.MAX_TRADE_ITEMS)
                    localReady = false
                    remoteReady = false
                    localCommit = false
                    remoteCommit = false
                    tradeCompleted = false
                    tradeStatus = "Trade offer updated live."
                    tab = PuppyExchangeTab.TRADE
                }
                "trade.ready" -> {
                    remoteReady = message.payload.optBoolean("ready", false)
                    remoteCommit = false
                    localCommit = false
                    tradeStatus = if (remoteReady) "Other player is ready." else "Other player changed their readiness."
                }
                "trade.commit" -> {
                    remoteCommit = true
                    tradeStatus = "Other player confirmed the trade."
                }
                "trade.complete" -> {
                    if (message.payload.optBoolean("success", false)) {
                        tradeStatus = "Trade completed on both devices."
                    }
                }
            }
        }
    }

    LaunchedEffect(
        localCommit,
        remoteCommit,
        localReady,
        remoteReady,
        localTradeIds,
        remoteTradeIds,
        tradeCompleted,
        peer?.playerId
    ) {
        if (
            !tradeCompleted &&
            localCommit &&
            remoteCommit &&
            localReady &&
            remoteReady &&
            localTradeIds.isNotEmpty() &&
            remoteTradeIds.isNotEmpty() &&
            peer != null
        ) {
            val success = vm.applyExchangeTrade(localTradeIds.toSet(), remoteTradeIds.toSet())
            if (success) {
                val transactionId = PuppyExchangeProtocol.newTransactionId()
                recordTradeHistory(
                    ledger = ledger,
                    transactionId = transactionId,
                    localPlayerId = PuppyPlayerIdentity.playerId(context),
                    remotePlayerId = peer.playerId,
                    localPuppyIds = localTradeIds,
                    remotePuppyIds = remoteTradeIds
                )
                tradeCompleted = true
                tradeStatus = "Trade completed."
                runCatching {
                    session.sendMessage(
                        "trade.complete",
                        JSONObject().put("success", true)
                    )
                }
            } else {
                tradeStatus = "Trade could not be applied. Recheck the current roster and offers."
                localCommit = false
                remoteCommit = false
                runCatching {
                    session.sendMessage(
                        "trade.complete",
                        JSONObject().put("success", false)
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Puppy Exchange", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(
                    "Direct device-to-device gifting and trading.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onBack) { Text("Back") }
        }

        Spacer(Modifier.height(8.dp))
        ExchangeIdentityHeader(connection, realtime)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PuppyExchangeTab.entries.forEach { item ->
                FilterChip(
                    selected = tab == item,
                    onClick = { tab = item },
                    label = { Text(item.title) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        when (tab) {
            PuppyExchangeTab.FRIENDS -> ExchangeFriendsPanel(
                ledger = ledger,
                connection = connection,
                incomingRequest = incomingFriendRequest,
                status = friendStatus,
                onAddFriend = {
                    if (peer == null) {
                        tab = PuppyExchangeTab.CONNECT
                    } else {
                        runCatching { session.sendMessage("friend.request") }
                            .onSuccess { friendStatus = "Friend request sent live to ${peer.username}." }
                            .onFailure { friendStatus = it.message ?: "Unable to send friend request." }
                    }
                },
                onAcceptRequest = {
                    if (peer != null) {
                        ledger.upsertFriend(
                            PuppyFriendRecord(
                                playerId = peer.playerId,
                                friendCode = peer.friendCode,
                                lastKnownUsername = peer.username,
                                addedAtMs = System.currentTimeMillis(),
                                blocked = false
                            )
                        )
                        runCatching {
                            session.sendMessage(
                                "friend.response",
                                JSONObject().put("accepted", true)
                            )
                        }
                        incomingFriendRequest = false
                        friendStatus = "${peer.username} added to Friends."
                    }
                },
                onDeclineRequest = {
                    runCatching {
                        session.sendMessage(
                            "friend.response",
                            JSONObject().put("accepted", false)
                        )
                    }
                    incomingFriendRequest = false
                    friendStatus = "Friend request declined."
                }
            )
            PuppyExchangeTab.CONNECT -> ExchangeConnectPanel(session, connection)
            PuppyExchangeTab.GIFTS -> ExchangeGiftsPanel(
                state = state,
                vm = vm,
                session = session,
                connection = connection,
                assets = assets,
                ledger = ledger,
                incomingGift = incomingGift,
                status = giftStatus,
                onStatus = { giftStatus = it },
                onClearIncoming = { incomingGift = null }
            )
            PuppyExchangeTab.TRADE -> ExchangeTradePanel(
                state = state,
                session = session,
                connection = connection,
                assets = assets,
                localTradeIds = localTradeIds,
                remoteTradeIds = remoteTradeIds,
                localReady = localReady,
                remoteReady = remoteReady,
                localCommit = localCommit,
                remoteCommit = remoteCommit,
                completed = tradeCompleted,
                status = tradeStatus,
                onLocalOffer = { next ->
                    localTradeIds = next
                    localReady = false
                    remoteReady = false
                    localCommit = false
                    remoteCommit = false
                    tradeCompleted = false
                    tradeStatus = "Your trade offer updated."
                    runCatching {
                        session.sendMessage(
                            "trade.offer",
                            JSONObject().put("puppyIds", JSONArray(next))
                        )
                    }
                },
                onReady = { ready ->
                    localReady = ready
                    localCommit = false
                    remoteCommit = false
                    runCatching {
                        session.sendMessage(
                            "trade.ready",
                            JSONObject().put("ready", ready)
                        )
                    }
                    tradeStatus = if (ready) "Ready sent. Waiting for the other player." else "You are no longer ready."
                },
                onCommit = {
                    localCommit = true
                    runCatching { session.sendMessage("trade.commit") }
                    tradeStatus = "Trade confirmation sent."
                }
            )
            PuppyExchangeTab.HISTORY -> ExchangeHistoryPanel(ledger)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ExchangeIdentityHeader(
    connection: ExchangeConnectionState,
    realtime: ExchangeRealtimeState
) {
    val context = LocalContext.current
    val username = remember { PuppyPlayerIdentity.username(context) }
    val friendCode = remember { PuppyPlayerIdentity.friendCode(context) }
    val connectionText = when (connection) {
        ExchangeConnectionState.Idle -> "Not connected"
        ExchangeConnectionState.CreatingOffer -> "Creating offer"
        ExchangeConnectionState.WaitingForAnswer -> "Waiting for answer"
        ExchangeConnectionState.ApplyingOffer -> "Applying offer"
        ExchangeConnectionState.Connecting -> "Connecting"
        is ExchangeConnectionState.Connected -> {
            val latency = realtime.latencyMs?.let { " · ${it} ms" }.orEmpty()
            if (realtime.peerOnline) "Live with ${connection.peer.username}$latency"
            else "Connection stale · waiting for ${connection.peer.username}"
        }
        is ExchangeConnectionState.Failed -> connection.message
        ExchangeConnectionState.Closed -> "Disconnected"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
    ) {
        Column(Modifier.padding(11.dp)) {
            Text(username, fontWeight = FontWeight.Black)
            Text(friendCode, style = MaterialTheme.typography.labelLarge)
            Text(connectionText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ExchangeFriendsPanel(
    ledger: PuppyExchangeLedger,
    connection: ExchangeConnectionState,
    incomingRequest: Boolean,
    status: String?,
    onAddFriend: () -> Unit,
    onAcceptRequest: () -> Unit,
    onDeclineRequest: () -> Unit
) {
    var refresh by remember { mutableStateOf(0) }
    val friends = remember(refresh, status) { ledger.snapshot().friends.sortedBy { it.lastKnownUsername } }
    val peer = (connection as? ExchangeConnectionState.Connected)?.peer

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text("Friends", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                "Friends are saved locally. New requests are sent live over the active direct connection.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(onClick = onAddFriend) {
            Text(if (peer == null) "Add Friend" else "Send Request")
        }
    }
    Spacer(Modifier.height(9.dp))

    if (incomingRequest && peer != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Friend request from ${peer.username}", fontWeight = FontWeight.Black)
                Text(peer.friendCode, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Button(onClick = {
                        onAcceptRequest()
                        refresh++
                    }) { Text("Accept") }
                    OutlinedButton(onClick = onDeclineRequest) { Text("Decline") }
                }
            }
        }
        Spacer(Modifier.height(9.dp))
    }

    if (peer != null) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Connected player", fontWeight = FontWeight.Black)
                Text(peer.username)
                Text(peer.friendCode, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(7.dp))
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
                }) { Text("Block Player") }
            }
        }
        Spacer(Modifier.height(9.dp))
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
    status?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, style = MaterialTheme.typography.bodySmall)
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

    Text("Direct Connect", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    Text(
        "Offer/Answer is only used to establish the direct WebRTC link. After connection, Friends, Gifts, and Trade update live without new codes.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(10.dp))

    OutlinedTextField(
        value = friendCode,
        onValueChange = { friendCode = it.uppercase().take(32) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Expected Friend Code") },
        singleLine = true
    )
    Spacer(Modifier.height(7.dp))
    Button(
        onClick = {
            scope.launch {
                runCatching { session.createOffer(friendCode) }
                    .onSuccess {
                        generatedCode = it
                        result = "Offer ready. Send it once; live sync begins after the answer is applied."
                    }
                    .onFailure { result = it.message ?: "Unable to create offer." }
            }
        },
        enabled = PuppyPlayerIdentity.isValidFriendCode(friendCode),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Create Offer Code") }

    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = offerCode,
        onValueChange = { offerCode = it.take(524_288) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Paste Offer Code") },
        minLines = 2,
        maxLines = 4
    )
    Spacer(Modifier.height(6.dp))
    OutlinedButton(
        onClick = {
            scope.launch {
                runCatching { session.acceptOfferAndCreateAnswer(offerCode) }
                    .onSuccess {
                        generatedCode = it
                        result = "Answer ready. Send it back to finish the direct connection."
                    }
                    .onFailure { result = it.message ?: "Unable to accept offer." }
            }
        },
        enabled = offerCode.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Create Answer Code") }

    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = answerCode,
        onValueChange = { answerCode = it.take(524_288) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Paste Answer Code") },
        minLines = 2,
        maxLines = 4
    )
    Spacer(Modifier.height(6.dp))
    OutlinedButton(
        onClick = {
            scope.launch {
                runCatching { session.applyAnswer(answerCode) }
                    .onSuccess { result = "Answer applied. Establishing live direct connection…" }
                    .onFailure { result = it.message ?: "Unable to apply answer." }
            }
        },
        enabled = answerCode.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Apply Answer Code") }

    if (generatedCode.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = generatedCode,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            label = { Text("Generated Connection Code") },
            minLines = 2,
            maxLines = 5
        )
    }
    result?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, style = MaterialTheme.typography.bodySmall)
    }
    if (connection is ExchangeConnectionState.Failed) {
        Spacer(Modifier.height(6.dp))
        Text(connection.message, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ExchangeGiftsPanel(
    state: V6GameState,
    vm: PuppyClickerV6ViewModel,
    session: PuppyExchangeSession,
    connection: ExchangeConnectionState,
    assets: List<PuppyRosterAsset>,
    ledger: PuppyExchangeLedger,
    incomingGift: LiveGiftOffer?,
    status: String?,
    onStatus: (String) -> Unit,
    onClearIncoming: () -> Unit
) {
    val context = LocalContext.current
    val peer = (connection as? ExchangeConnectionState.Connected)?.peer
    var selectedPuppyId by remember { mutableStateOf<String?>(null) }
    val giftable = assets.filter {
        it.style.id in state.unlockedPuppies &&
            it.transferPolicy.giftable &&
            !it.transferPolicy.bound
    }

    Text("Gifts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    if (peer == null) {
        Text("Connect to a verified friend before sending or accepting a gift.")
        return
    }
    Text("Live recipient: ${peer.username} · ${peer.friendCode}", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))

    incomingGift?.let { gift ->
        val asset = DynamicPuppyRoster.asset(gift.puppyId)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Incoming Gift", fontWeight = FontWeight.Black)
                Text(asset?.style?.name ?: gift.puppyId)
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Button(
                        onClick = {
                            val allowed = asset?.transferPolicy?.giftable == true && !asset.transferPolicy.bound
                            val accepted = allowed && vm.receiveExchangePuppy(gift.puppyId)
                            runCatching {
                                session.sendMessage(
                                    "gift.response",
                                    JSONObject()
                                        .put("offerId", gift.offerId)
                                        .put("puppyId", gift.puppyId)
                                        .put("accepted", accepted)
                                )
                            }
                            if (accepted) {
                                recordGiftHistory(
                                    ledger = ledger,
                                    transactionId = gift.offerId,
                                    localPlayerId = PuppyPlayerIdentity.playerId(context),
                                    remotePlayerId = peer.playerId,
                                    puppyId = gift.puppyId
                                )
                                onStatus("Gift accepted and added to your roster.")
                            } else {
                                onStatus("Gift could not be accepted.")
                            }
                            onClearIncoming()
                        },
                        enabled = asset?.transferPolicy?.giftable == true && !asset.transferPolicy.bound
                    ) { Text("Accept") }
                    OutlinedButton(onClick = {
                        runCatching {
                            session.sendMessage(
                                "gift.response",
                                JSONObject()
                                    .put("offerId", gift.offerId)
                                    .put("puppyId", gift.puppyId)
                                    .put("accepted", false)
                            )
                        }
                        onStatus("Gift declined.")
                        onClearIncoming()
                    }) { Text("Decline") }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    if (giftable.isEmpty()) {
        Text("No currently unlocked puppies are marked giftable by the live roster policy.")
    } else {
        Text("Choose a puppy", fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            giftable.forEach { asset ->
                FilterChip(
                    selected = selectedPuppyId == asset.style.id,
                    onClick = { selectedPuppyId = asset.style.id },
                    label = { Text(asset.style.name) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val puppyId = selectedPuppyId ?: return@Button
                val transactionId = PuppyExchangeProtocol.newTransactionId()
                runCatching {
                    session.sendMessage(
                        "gift.offer",
                        JSONObject()
                            .put("offerId", transactionId)
                            .put("puppyId", puppyId)
                    )
                }.onSuccess {
                    onStatus("Gift offer sent live to ${peer.username}.")
                }.onFailure {
                    onStatus(it.message ?: "Unable to send gift offer.")
                }
            },
            enabled = selectedPuppyId != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Send Gift Offer") }
    }
    status?.let {
        Spacer(Modifier.height(7.dp))
        Text(it, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ExchangeTradePanel(
    state: V6GameState,
    session: PuppyExchangeSession,
    connection: ExchangeConnectionState,
    assets: List<PuppyRosterAsset>,
    localTradeIds: List<String>,
    remoteTradeIds: List<String>,
    localReady: Boolean,
    remoteReady: Boolean,
    localCommit: Boolean,
    remoteCommit: Boolean,
    completed: Boolean,
    status: String?,
    onLocalOffer: (List<String>) -> Unit,
    onReady: (Boolean) -> Unit,
    onCommit: () -> Unit
) {
    val peer = (connection as? ExchangeConnectionState.Connected)?.peer
    val tradeable = assets.filter {
        it.style.id in state.unlockedPuppies &&
            it.transferPolicy.tradeable &&
            !it.transferPolicy.bound &&
            !it.transferPolicy.sourceCopy
    }

    Text("Trade", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    if (peer == null) {
        Text("Connect to a verified friend to build a live trade.")
        return
    }
    Text("Live trade with ${peer.username}", style = MaterialTheme.typography.bodySmall)
    Text("Up to ${PuppyExchangeLedger.MAX_TRADE_ITEMS} puppies per side. Editing either offer resets Ready.", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))

    Text("Your offer", fontWeight = FontWeight.Bold)
    if (tradeable.isEmpty()) {
        Text("No unlocked puppies are currently marked tradeable.")
    } else {
        tradeable.forEach { asset ->
            val selected = asset.style.id in localTradeIds
            FilterChip(
                selected = selected,
                onClick = {
                    val next = if (selected) {
                        localTradeIds - asset.style.id
                    } else if (localTradeIds.size < PuppyExchangeLedger.MAX_TRADE_ITEMS) {
                        localTradeIds + asset.style.id
                    } else {
                        localTradeIds
                    }
                    if (next != localTradeIds) onLocalOffer(next)
                },
                label = { Text(asset.style.name) }
            )
            Spacer(Modifier.height(3.dp))
        }
    }

    Spacer(Modifier.height(8.dp))
    Text("Their offer", fontWeight = FontWeight.Bold)
    if (remoteTradeIds.isEmpty()) {
        Text("Waiting for the other player to choose puppies.")
    } else {
        remoteTradeIds.forEach { id ->
            Text("• ${DynamicPuppyRoster.asset(id)?.style?.name ?: id}")
        }
    }

    Spacer(Modifier.height(10.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(10.dp)) {
            Text("You: ${if (localReady) "Ready" else "Editing"}", fontWeight = FontWeight.Bold)
            Text("${peer.username}: ${if (remoteReady) "Ready" else "Editing"}", fontWeight = FontWeight.Bold)
            if (localCommit || remoteCommit) {
                Text(
                    "Confirmations · You ${if (localCommit) "✓" else "…"} · Other ${if (remoteCommit) "✓" else "…"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))

    Button(
        onClick = { onReady(!localReady) },
        enabled = !completed && localTradeIds.isNotEmpty() && remoteTradeIds.isNotEmpty(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (localReady) "Cancel Ready" else "Ready Trade")
    }
    Spacer(Modifier.height(6.dp))
    OutlinedButton(
        onClick = onCommit,
        enabled = !completed && localReady && remoteReady && !localCommit,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (localCommit) "Confirmed" else "Confirm Trade")
    }

    status?.let {
        Spacer(Modifier.height(7.dp))
        Text(it, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ExchangeHistoryPanel(ledger: PuppyExchangeLedger) {
    val transactions = ledger.snapshot().transactions.sortedByDescending { it.createdAtMs }
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

private fun recordGiftHistory(
    ledger: PuppyExchangeLedger,
    transactionId: String,
    localPlayerId: String,
    remotePlayerId: String,
    puppyId: String
) {
    ledger.putTransaction(
        ExchangeTransactionRecord(
            transactionId = transactionId,
            type = ExchangeTransactionType.GIFT,
            playerAId = localPlayerId,
            playerBId = remotePlayerId,
            offerARecordIds = listOf(puppyId),
            offerBRecordIds = emptyList(),
            offerAHash = PuppyExchangeProtocol.canonicalOfferHash(listOf(puppyId)),
            offerBHash = PuppyExchangeProtocol.canonicalOfferHash(emptyList()),
            createdAtMs = System.currentTimeMillis(),
            state = ExchangeTransactionState.COMPLETED
        )
    )
}

private fun recordTradeHistory(
    ledger: PuppyExchangeLedger,
    transactionId: String,
    localPlayerId: String,
    remotePlayerId: String,
    localPuppyIds: List<String>,
    remotePuppyIds: List<String>
) {
    ledger.putTransaction(
        ExchangeTransactionRecord(
            transactionId = transactionId,
            type = ExchangeTransactionType.TRADE,
            playerAId = localPlayerId,
            playerBId = remotePlayerId,
            offerARecordIds = localPuppyIds,
            offerBRecordIds = remotePuppyIds,
            offerAHash = PuppyExchangeProtocol.canonicalOfferHash(localPuppyIds),
            offerBHash = PuppyExchangeProtocol.canonicalOfferHash(remotePuppyIds),
            createdAtMs = System.currentTimeMillis(),
            state = ExchangeTransactionState.COMPLETED
        )
    )
}

private fun JSONArray?.stringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}
