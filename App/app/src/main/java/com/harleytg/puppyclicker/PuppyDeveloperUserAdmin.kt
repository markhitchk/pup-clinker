package com.harleytg.puppyclicker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
internal fun PuppyDeveloperUserAdminScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var users by remember { mutableStateOf<List<PupAdminUserSummary>>(emptyList()) }
    var selected by remember { mutableStateOf<PupAdminUserDetail?>(null) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    fun loadUsers(search: String = query) {
        scope.launch {
            loading = true
            status = null
            runCatching { SupabasePupEyeClient.listAdminUsers(context, search) }
                .onSuccess { users = it }
                .onFailure { status = it.message ?: "Unable to load Pup Accounts." }
            loading = false
        }
    }

    fun openUser(user: PupAdminUserSummary) {
        scope.launch {
            loading = true
            status = null
            runCatching { SupabasePupEyeClient.readAdminUser(context, user.playerUuid) }
                .onSuccess { selected = it }
                .onFailure { status = it.message ?: "Unable to load user data." }
            loading = false
        }
    }

    fun refreshSelected() {
        val target = selected?.user?.playerUuid ?: return
        scope.launch {
            runCatching { SupabasePupEyeClient.readAdminUser(context, target) }
                .onSuccess { selected = it }
                .onFailure { status = it.message ?: "Unable to refresh user data." }
        }
    }

    LaunchedEffect(Unit) {
        loading = true
        runCatching { SupabasePupEyeClient.listAdminUsers(context) }
            .onSuccess { users = it }
            .onFailure { status = it.message ?: "Unable to load Pup Accounts." }
        loading = false
    }

    BackHandler {
        if (selected != null) selected = null else onBack()
    }

    selected?.let { detail ->
        PuppyDeveloperUserEditor(
            detail = detail,
            status = status,
            onStatus = { status = it },
            onBack = { selected = null },
            onRefresh = ::refreshSelected
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 0.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹ Developer Console") }
        Text(
            "Pup Account Admin",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        Text(
            "View users, edit cloud game data, manage items, and send system notifications.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(80) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search username, Player ID, Friend Code or Discord") }
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { loadUsers() },
                modifier = Modifier.weight(1f),
                enabled = !loading
            ) { Text(if (loading) "Loading…" else "Search Users") }
            OutlinedButton(
                onClick = {
                    query = ""
                    loadUsers("")
                },
                modifier = Modifier.weight(1f),
                enabled = !loading
            ) { Text("Show All") }
        }

        status?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            users.size.toString() + " users",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(users, key = { it.playerUuid }) { user ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openUser(user) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                user.username,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                user.guildRole ?: "PLAYER",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text("Player ID " + user.playerId, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Friend " + user.friendCode + " · Revision " + (user.cloudRevision ?: 0L),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        user.discordUsername?.let {
                            Text(
                                "Discord @" + it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PuppyDeveloperUserEditor(
    detail: PupAdminUserDetail,
    status: String?,
    onStatus: (String?) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val game = detail.game
    var treats by remember(detail.user.playerUuid, game?.treats) { mutableStateOf(game?.treats?.toString().orEmpty()) }
    var lifetimeTreats by remember(detail.user.playerUuid, game?.lifetimeTreats) { mutableStateOf(game?.lifetimeTreats?.toString().orEmpty()) }
    var bones by remember(detail.user.playerUuid, game?.bones) { mutableStateOf(game?.bones?.toString().orEmpty()) }
    var pupCoins by remember(detail.user.playerUuid, game?.pupCoins) { mutableStateOf(game?.pupCoins?.toString().orEmpty()) }
    var casinoChips by remember(detail.user.playerUuid, game?.casinoChips) { mutableStateOf(game?.casinoChips?.toString().orEmpty()) }
    var happiness by remember(detail.user.playerUuid, game?.happiness) { mutableStateOf(game?.happiness?.toString().orEmpty()) }
    var fullness by remember(detail.user.playerUuid, game?.fullness) { mutableStateOf(game?.fullness?.toString().orEmpty()) }
    var energy by remember(detail.user.playerUuid, game?.energy) { mutableStateOf(game?.energy?.toString().orEmpty()) }
    var cleanliness by remember(detail.user.playerUuid, game?.cleanliness) { mutableStateOf(game?.cleanliness?.toString().orEmpty()) }
    var bond by remember(detail.user.playerUuid, game?.bond) { mutableStateOf(game?.bond?.toString().orEmpty()) }
    var itemType by rememberSaveable(detail.user.playerUuid) { mutableStateOf("puppy") }
    var itemId by rememberSaveable(detail.user.playerUuid) { mutableStateOf("") }
    var ticketCount by rememberSaveable(detail.user.playerUuid) { mutableStateOf("0") }
    var messageTitle by rememberSaveable(detail.user.playerUuid) { mutableStateOf("Message from Puppy Clicker") }
    var messageBody by rememberSaveable(detail.user.playerUuid) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun runAction(successMessage: String, block: suspend () -> Unit) {
        scope.launch {
            busy = true
            onStatus(null)
            runCatching { block() }
                .onSuccess {
                    onStatus(successMessage)
                    onRefresh()
                }
                .onFailure { onStatus(it.message ?: "Developer action failed.") }
            busy = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("‹ Users") }
            Text(
                detail.user.username,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                detail.user.playerId + " · " + detail.user.friendCode,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            status?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            AdminSection("Account") {
                AdminMetric("Player UUID", detail.user.playerUuid)
                AdminMetric("Role", detail.user.guildRole ?: "PLAYER")
                AdminMetric("Status", detail.user.status)
                AdminMetric("Save revision", detail.user.cloudRevision?.toString() ?: "No save")
                AdminMetric("Save generation", detail.cloudGeneration?.toString() ?: "No save")
                AdminMetric("Multiple devices", if (detail.allowMultipleDevices) "Allowed" else "Single device")
            }
        }

        if (game != null) {
            item {
                AdminSection("Game data") {
                    Text(
                        "Saving creates a new authoritative Pup Account revision.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    AdminNumberField("Treats", treats) { treats = it }
                    AdminNumberField("Lifetime Treats", lifetimeTreats) { lifetimeTreats = it }
                    AdminNumberField("Bones", bones) { bones = it }
                    AdminNumberField("Pup Coins", pupCoins) { pupCoins = it }
                    AdminNumberField("Casino Chips", casinoChips) { casinoChips = it }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Active puppy care · " + game.activePuppy, fontWeight = FontWeight.Bold)
                    AdminNumberField("Happiness", happiness) { happiness = it }
                    AdminNumberField("Fullness", fullness) { fullness = it }
                    AdminNumberField("Energy", energy) { energy = it }
                    AdminNumberField("Cleanliness", cleanliness) { cleanliness = it }
                    AdminNumberField("Bond", bond) { bond = it }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val fields = JSONObject()
                            listOf(
                                "treats" to treats,
                                "lifetimeTreats" to lifetimeTreats,
                                "bones" to bones,
                                "pupCoins" to pupCoins,
                                "casinoChips" to casinoChips,
                                "happiness" to happiness,
                                "fullness" to fullness,
                                "energy" to energy,
                                "cleanliness" to cleanliness,
                                "bond" to bond
                            ).forEach { pair ->
                                pair.second.trim().toLongOrNull()?.let { fields.put(pair.first, it) }
                            }
                            runAction("User game data updated.") {
                                SupabasePupEyeClient.updateAdminGameData(
                                    context,
                                    detail.user.playerUuid,
                                    fields
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
                    ) { Text("Save Game Data") }
                }
            }

            item {
                AdminSection("Items") {
                    Text(
                        "Puppies: " + game.unlockedPuppies.sorted().joinToString(", "),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Accessories: " + game.ownedAccessories.sorted().joinToString(", "),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Tickets: " + game.tickets.entries.joinToString(" · ") {
                            it.key.uppercase() + " " + it.value
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("puppy", "accessory", "ticket").forEach { type ->
                            FilterChip(
                                selected = itemType == type,
                                onClick = {
                                    itemType = type
                                    itemId = if (type == "ticket") "common" else ""
                                },
                                label = { Text(type.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = itemId,
                        onValueChange = { itemId = it.take(120) },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                if (itemType == "ticket") {
                                    "Rarity: common/uncommon/rare/epic/legendary"
                                } else {
                                    "Item ID"
                                }
                            )
                        },
                        singleLine = true
                    )
                    if (itemType == "ticket") {
                        AdminNumberField("Ticket count", ticketCount) { ticketCount = it }
                        Button(
                            onClick = {
                                val count = ticketCount.toIntOrNull() ?: return@Button
                                runAction("Ticket inventory updated.") {
                                    SupabasePupEyeClient.setAdminItem(
                                        context = context,
                                        targetPlayerUuid = detail.user.playerUuid,
                                        itemType = "ticket",
                                        itemId = itemId,
                                        count = count
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && itemId.isNotBlank()
                        ) { Text("Set Ticket Count") }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    runAction("Item granted.") {
                                        SupabasePupEyeClient.setAdminItem(
                                            context,
                                            detail.user.playerUuid,
                                            itemType,
                                            itemId,
                                            present = true
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !busy && itemId.isNotBlank()
                            ) { Text("Add Item") }
                            OutlinedButton(
                                onClick = {
                                    runAction("Item removed.") {
                                        SupabasePupEyeClient.setAdminItem(
                                            context,
                                            detail.user.playerUuid,
                                            itemType,
                                            itemId,
                                            present = false
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !busy && itemId.isNotBlank()
                            ) { Text("Remove Item") }
                        }
                    }
                }
            }
        } else {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        "This user has no Pup Account cloud save yet, so game data cannot be edited.",
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        item {
            AdminSection("System message") {
                OutlinedTextField(
                    value = messageTitle,
                    onValueChange = { messageTitle = it.take(100) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Title") },
                    singleLine = true
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = messageBody,
                    onValueChange = { messageBody = it.take(1_500) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Message") },
                    minLines = 3
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        runAction("System message sent.") {
                            SupabasePupEyeClient.sendAdminMessage(
                                context,
                                detail.user.playerUuid,
                                messageTitle,
                                messageBody
                            )
                        }
                        messageBody = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && messageTitle.isNotBlank() && messageBody.isNotBlank()
                ) { Text("Send to Notifications") }
            }
        }
    }
}

@Composable
private fun AdminSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun AdminMetric(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AdminNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() }.take(16)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true
    )
    Spacer(Modifier.height(6.dp))
}
