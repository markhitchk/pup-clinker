#!/usr/bin/env python3
"""Finish the schema-2 Puppy Code claim path in the generated V6 sources."""
from pathlib import Path
import sys

PACKAGE = Path("com/harleytg/puppyclicker")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"Puppy Code V2 patch {label}: expected 1 match, found {count}")
    return source.replace(old, new, 1)


def replace_function(source: str, signature: str, next_signature: str, replacement: str, label: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise RuntimeError(f"Puppy Code V2 patch {label}: start signature not found")
    end = source.find(next_signature, start)
    if end < 0:
        raise RuntimeError(f"Puppy Code V2 patch {label}: end signature not found")
    return source[:start] + replacement.rstrip() + "\n\n" + source[end:]


def patch_view_model(source: str) -> str:
    source = replace_once(
        source,
        "    private var suspicionWindowStartedMs = 0L\n",
        "    private var suspicionWindowStartedMs = 0L\n    private var puppyCodeClaimInFlight = false\n",
        "claim serialization state",
    )

    source = replace_function(
        source,
        "    fun redeemCode(rawCode: String): V6RedeemOutcome {",
        "    fun prestige() {",
        '''    fun redeemCode(rawCode: String, onResult: (V6RedeemOutcome) -> Unit) {
        if (rawCode.isEmpty() || rawCode.length > 64) {
            onResult(V6RedeemOutcome(false, "Invalid Puppy Code."))
            return
        }
        if (puppyCodeClaimInFlight) {
            onResult(V6RedeemOutcome(false, "A Puppy Code is already being checked."))
            return
        }

        puppyCodeClaimInFlight = true
        viewModelScope.launch {
            try {
                when (val refresh = StreamedRedeemCodes.refreshForClaim(getApplication())) {
                    is PuppyCodeRefreshResult.Unavailable -> {
                        onResult(V6RedeemOutcome(false, refresh.message))
                    }

                    is PuppyCodeRefreshResult.Authorized -> {
                        val definition = PuppyCodeCatalog.findExact(rawCode, refresh.snapshot)
                        if (definition == null) {
                            onResult(V6RedeemOutcome(false, "Invalid Puppy Code."))
                            return@launch
                        }

                        val current = _state.value
                        val validation = PuppyCodeValidator.validate(
                            definition = definition,
                            versionCode = BuildConfig.VERSION_CODE,
                            redeemedIds = current.redeemedCodeIds,
                            authoritativeTimeMs = refresh.authoritativeTimeMs,
                            channel = if (BuildConfig.DEBUG) "dev" else "stable"
                        )
                        if (validation is PuppyCodeValidationResult.Rejected) {
                            onResult(V6RedeemOutcome(false, validation.message))
                            return@launch
                        }

                        val grant = RewardGrantEngine.applyTo(current, definition.rewards)
                        if (!grant.success) {
                            onResult(
                                V6RedeemOutcome(
                                    false,
                                    "This Puppy Code contains rewards that this version cannot safely apply. Update Puppy Clicker and try again."
                                )
                            )
                            return@launch
                        }

                        val next = grant.state.copy(
                            redeemedCodeIds = current.redeemedCodeIds + definition.id
                        )
                        val oldHistory = PuppyCodeHistory.mergeLegacyIds(
                            PuppyCodeHistory.decode(prefs.getString(KEY_PUPPY_CODE_HISTORY, null)),
                            current.redeemedCodeIds
                        )
                        val newEntry = PuppyCodeHistory.createEntry(
                            definition = definition,
                            redeemedAtMs = refresh.authoritativeTimeMs ?: System.currentTimeMillis()
                        )
                        val nextHistory = listOf(newEntry) + oldHistory.filterNot {
                            it.redemptionId == newEntry.redemptionId
                        }

                        if (!commitPuppyCodeClaim(next, nextHistory)) {
                            onResult(V6RedeemOutcome(false, "The reward could not be saved, so nothing was granted. Try again."))
                            return@launch
                        }

                        _state.value = next
                        onResult(V6RedeemOutcome(true, definition.message))
                    }
                }
            } finally {
                puppyCodeClaimInFlight = false
            }
        }
    }

    private fun commitPuppyCodeClaim(
        next: V6GameState,
        history: List<PuppyCodeHistoryEntry>
    ): Boolean = prefs.edit().apply {
        putLong(KEY_TREATS, next.treats)
        putLong(KEY_LIFETIME, next.lifetimeTreats)
        putStringSet(KEY_UNLOCKED_PUPPIES, next.unlockedPuppies)
        putString(KEY_PUPPY_STYLE, next.puppyStyle)
        TicketRarity.entries.forEach { rarity ->
            putInt(ticketKey(rarity), next.ticketInventory[rarity] ?: 0)
        }
        putStringSet(KEY_REDEEMED_CODES, next.redeemedCodeIds)
        putString(KEY_PUPPY_CODE_HISTORY, PuppyCodeHistory.encode(history))
        putLong(KEY_LAST_SEEN, System.currentTimeMillis())
    }.commit()''',
        "live claim implementation",
    )

    source = replace_once(
        source,
        '        private const val KEY_REDEEMED_CODES = "redeemed_code_ids"\n',
        '        private const val KEY_REDEEMED_CODES = "redeemed_code_ids"\n        private const val KEY_PUPPY_CODE_HISTORY = "puppy_code_history_v2"\n',
        "history preference key",
    )
    return source


def patch_activity(source: str) -> str:
    return replace_function(
        source,
        "@Composable\nprivate fun V6RedeemDialog(vm: PuppyClickerV6ViewModel, close: () -> Unit) {",
        "@Composable\nprivate fun V6Pups",
        '''@Composable
private fun V6RedeemDialog(vm: PuppyClickerV6ViewModel, close: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var success by rememberSaveable { mutableStateOf(false) }
    var checking by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!checking) close() },
        title = { Text("🎫 Redeem Puppy Code") },
        text = {
            Column {
                Text("Enter the Puppy Code exactly as provided. Every new claim is checked live against the GitHub catalogue; offline or failed validation never grants a reward.")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        if (!checking) {
                            code = it.take(64)
                            message = null
                        }
                    },
                    label = { Text("Puppy Code") },
                    singleLine = true,
                    enabled = !checking,
                    modifier = Modifier.fillMaxWidth()
                )
                if (checking) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Checking live Puppy Code catalogue…", style = MaterialTheme.typography.bodySmall)
                }
                message?.let {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = if (success) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(it, Modifier.padding(9.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    checking = true
                    success = false
                    message = null
                    vm.redeemCode(code) { result ->
                        checking = false
                        success = result.success
                        message = result.message
                        if (result.success) code = ""
                    }
                },
                enabled = code.isNotEmpty() && !checking
            ) {
                Text(if (checking) "Checking…" else "Redeem")
            }
        },
        dismissButton = {
            TextButton(onClick = close, enabled = !checking) { Text("Close") }
        }
    )
}''',
        "async redeem dialog",
    )


def main(root: Path) -> None:
    view_model = root / PACKAGE / "PuppyClickerV6ViewModel.kt"
    view_model.write_text(
        patch_view_model(view_model.read_text(encoding="utf-8")),
        encoding="utf-8",
    )

    activity = root / PACKAGE / "PuppyClickerV6Activity.kt"
    activity.write_text(
        patch_activity(activity.read_text(encoding="utf-8")),
        encoding="utf-8",
    )
    print("Puppy Code schema-2 V6 claim path integrated")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_puppy_codes_v2.py GENERATED_SOURCE_ROOT")
    main(Path(sys.argv[1]))
