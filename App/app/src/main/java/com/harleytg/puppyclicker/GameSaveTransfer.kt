package com.harleytg.puppyclicker

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

internal data class SaveTransferResult(val success: Boolean, val message: String)

internal enum class PuppySavePasswordRequirement {
    REQUIRED,
    NOT_REQUIRED,
    UNKNOWN
}

/** Password-protected AES-256-GCM save transfer format. */
internal object GameSaveTransfer {
    private const val PAYLOAD_FORMAT = "puppy-clicker-transfer-payload"
    private const val PAYLOAD_VERSION = 3
    private const val LEGACY_FORMAT = "puppy-clicker-save"
    private const val ENCRYPTED_TRANSFER_FORMAT = "puppy-clicker-encrypted-save"
    private const val MAIN_PREFS = PuppyClickerV6ViewModel.PREFS_NAME
    private const val SEASONAL_PREFS = "puppy_seasonal_v1"
    private const val MAX_IMPORT_BYTES = 4 * 1024 * 1024

    fun export(context: Context, uri: Uri, password: String): SaveTransferResult = runCatching {
        require(password.length >= 8) { "Backup password must be at least 8 characters" }
        val stores = JSONObject().apply {
            put(MAIN_PREFS, SecurePreferenceCodec.encode(context.getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE)))
            put(SEASONAL_PREFS, SecurePreferenceCodec.encode(context.getSharedPreferences(SEASONAL_PREFS, Context.MODE_PRIVATE)))
        }
        val payload = JSONObject().apply {
            put("format", PAYLOAD_FORMAT)
            put("version", PAYLOAD_VERSION)
            put("package", context.packageName)
            put("exportedAtEpochMs", System.currentTimeMillis())
            put("identity", PuppyPlayerIdentity.metadata(context))
            put("stores", stores)
        }
        val encrypted = PuppySaveCrypto.encryptTransfer(
            payload.toString().toByteArray(Charsets.UTF_8),
            password.toCharArray()
        )

        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: error("Unable to open the selected export file")
        output.use { it.write(encrypted) }
        SaveTransferResult(true, "Encrypted save exported for ${PuppyPlayerIdentity.username(context)}.")
    }.getOrElse { error ->
        SaveTransferResult(false, "Export failed: ${error.message ?: "unknown error"}")
    }

    fun import(context: Context, uri: Uri, password: String): SaveTransferResult = runCatching {
        val bytes = readBounded(context, uri)
        val root = JSONObject(bytes.toString(Charsets.UTF_8))

        if (root.optString("format") == LEGACY_FORMAT) {
            importLegacy(context, root)
        } else {
            require(password.length >= 8) { "Enter the backup password used for this save" }
            val plain = try {
                PuppySaveCrypto.decryptTransfer(bytes, password.toCharArray())
            } catch (error: Exception) {
                PupEyeSaveGuard.recordTamper(context, "Encrypted import failed authentication")
                throw IllegalArgumentException("Wrong password or modified save file")
            }
            val payload = JSONObject(plain.toString(Charsets.UTF_8))
            require(payload.optString("format") == PAYLOAD_FORMAT) { "Invalid decrypted save payload" }
            require(payload.optInt("version") == PAYLOAD_VERSION) { "Unsupported save payload version" }

            val identity = payload.optJSONObject("identity")
                ?: error("Encrypted save is missing player identity")
            validateImportedIdentity(context, identity)
            restoreStores(context, payload.getJSONObject("stores"))
            PuppyPlayerIdentity.applyImportedUsername(context, identity)
        }

        val mainPrefs = context.getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE)
        PupEyeSaveGuard.seal(context, mainPrefs)
        ExternalGameSave.write(context, mainPrefs)
        SaveTransferResult(true, "Save imported and authenticated for ${PuppyPlayerIdentity.username(context)}. Your protected progress is ready to reload.")
    }.getOrElse { error ->
        SaveTransferResult(false, "Import failed: ${error.message ?: "unknown error"}")
    }

    fun suggestedFileName(context: Context): String {
        val username = PuppyPlayerIdentity.username(context)
        return "puppy_clicker_${username}_v3.pupsave"
    }


    fun passwordRequirement(
        context: Context,
        uri: Uri
    ): PuppySavePasswordRequirement =
        passwordRequirementFromBytes(readBounded(context, uri))

    fun passwordRequirementFromBytes(bytes: ByteArray): PuppySavePasswordRequirement = runCatching {
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        when (root.optString("format")) {
            LEGACY_FORMAT -> PuppySavePasswordRequirement.NOT_REQUIRED
            ENCRYPTED_TRANSFER_FORMAT -> PuppySavePasswordRequirement.REQUIRED
            else -> PuppySavePasswordRequirement.UNKNOWN
        }
    }.getOrDefault(PuppySavePasswordRequirement.UNKNOWN)

    private fun validateImportedIdentity(context: Context, identity: JSONObject) {
        val importedUsername = PuppyPlayerIdentity.normalizeUsername(identity.optString("username"))
        val importedDevice = identity.optString("deviceModel").trim().lowercase()
        require(importedUsername.isNotBlank()) { "Encrypted save has no valid username" }
        require(importedDevice.isNotBlank()) { "Encrypted save has no source device model" }

        val currentUsername = PuppyPlayerIdentity.username(context)
        if (currentUsername != "localplayer" && currentUsername != importedUsername) {
            throw IllegalArgumentException(
                "This save belongs to '$importedUsername', not '$currentUsername'."
            )
        }
    }

    private fun restoreStores(context: Context, stores: JSONObject) {
        val mainStore = stores.optJSONObject(MAIN_PREFS)
            ?: error("Save does not contain the main game store")
        SecurePreferenceCodec.restore(
            context.getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE),
            mainStore
        )
        stores.optJSONObject(SEASONAL_PREFS)?.let { seasonalStore ->
            SecurePreferenceCodec.restore(
                context.getSharedPreferences(SEASONAL_PREFS, Context.MODE_PRIVATE),
                seasonalStore
            )
        }
    }

    /** Read-only migration path for saves created before encrypted v3 backups. */
    private fun importLegacy(context: Context, root: JSONObject) {
        val version = root.optInt("version", 1)
        require(version in 1..2) { "Unsupported legacy save version $version" }
        val mainPrefs = context.getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE)
        if (version >= 2 && root.has("stores")) {
            val stores = root.getJSONObject("stores")
            restoreStores(context, stores)
        } else {
            val legacyValues = root.optJSONObject("values")
                ?: error("Legacy save is missing values")
            restoreLegacyStore(mainPrefs, legacyValues)
        }
    }

    private fun restoreLegacyStore(prefs: SharedPreferences, values: JSONObject) {
        val editor = prefs.edit().clear()
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = values.get(key)
            when (inferLegacyType(prefs, key, value)) {
                "string" -> editor.putString(key, value.toString())
                "boolean" -> editor.putBoolean(key, value as? Boolean ?: value.toString().toBoolean())
                "int" -> editor.putInt(key, (value as Number).toInt())
                "long" -> editor.putLong(key, (value as Number).toLong())
                "float" -> editor.putFloat(key, (value as Number).toFloat())
                "string_set" -> {
                    val array = value as JSONArray
                    val set = buildSet {
                        for (index in 0 until array.length()) add(array.getString(index))
                    }
                    editor.putStringSet(key, set)
                }
            }
        }
        check(editor.commit()) { "Unable to write imported legacy save" }
    }

    private fun inferLegacyType(prefs: SharedPreferences, key: String, value: Any): String = when (prefs.all[key]) {
        is String -> "string"
        is Boolean -> "boolean"
        is Int -> "int"
        is Long -> "long"
        is Float -> "float"
        is Set<*> -> "string_set"
        else -> when (value) {
            is String -> "string"
            is Boolean -> "boolean"
            is JSONArray -> "string_set"
            is Number -> if (isLegacyIntKey(key)) "int" else "long"
            else -> "string"
        }
    }

    private fun isLegacyIntKey(key: String): Boolean =
        key.startsWith("upgrade_") ||
            key.startsWith("prestige_skill_") ||
            key in setOf(
                "happiness", "fullness", "energy", "cleanliness_v5", "bond_v5",
                "best_combo", "daily_streak_v5", "pup_eye_strikes", "prestige_count_v6",
                "prestige_skill_points_v6", "birthday_month", "birthday_day"
            )

    private fun readBounded(context: Context, uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Unable to open the selected save file")
        return input.use { stream ->
            val buffer = ByteArray(MAX_IMPORT_BYTES + 1)
            var total = 0
            while (total < buffer.size) {
                val read = stream.read(buffer, total, buffer.size - total)
                if (read < 0) break
                total += read
            }
            if (total > MAX_IMPORT_BYTES) error("Save file is larger than 4 MB")
            buffer.copyOf(total)
        }
    }
}

@Composable
internal fun OnboardingSaveImport(onImportSuccess: () -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var popupTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var popupMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var importedSuccessfully by rememberSaveable { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            focusManager.clearFocus(force = true)
            val result = GameSaveTransfer.import(context, uri, password)
            popupTitle = if (result.success) "Save Imported" else "Import Failed"
            popupMessage = result.message
            importedSuccessfully = result.success
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Text("Import Existing Save", fontWeight = FontWeight.Black)
            Text(
                "Already play Puppy Clicker? Restore a .pupsave and continue setup with that player's username and progress.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.take(128) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Backup password") },
                placeholder = { Text("Only required for encrypted v3 saves") },
                supportingText = { Text("Legacy saves can be selected without a password.") },
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
            Spacer(Modifier.size(8.dp))
            OutlinedButton(
                onClick = {
                    importLauncher.launch(
                        arrayOf("application/octet-stream", "application/json", "*/*")
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Choose .pupsave")
            }
            Text(
                "Import keeps this device's Player ID and Friend Code. The save's display username and game progress are restored.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }

    if (popupTitle != null && popupMessage != null) {
        AlertDialog(
            onDismissRequest = {
                if (!importedSuccessfully) {
                    popupTitle = null
                    popupMessage = null
                }
            },
            title = { Text(popupTitle ?: "Save Import", fontWeight = FontWeight.Black) },
            text = { Text(popupMessage ?: "") },
            confirmButton = {
                Button(
                    onClick = {
                        val success = importedSuccessfully
                        popupTitle = null
                        popupMessage = null
                        importedSuccessfully = false
                        if (success) onImportSuccess()
                    }
                ) {
                    Text(if (importedSuccessfully) "Continue Setup" else "OK")
                }
            }
        )
    }
}

@Composable
internal fun SaveTransferSettings(onImportSuccess: (() -> Unit)? = null) {
    val context = LocalContext.current
    val activity = context as? Activity
    val focusManager = LocalFocusManager.current
    var username by rememberSaveable { mutableStateOf(PuppyPlayerIdentity.username(context)) }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var popupTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var popupMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var popupSuccess by rememberSaveable { mutableStateOf(false) }
    var reloadAfterPopup by rememberSaveable { mutableStateOf(false) }

    fun showPopup(title: String, result: SaveTransferResult, reloadOnSuccess: Boolean = false) {
        focusManager.clearFocus(force = true)
        popupTitle = title
        popupMessage = result.message
        popupSuccess = result.success
        reloadAfterPopup = result.success && reloadOnSuccess
    }

    fun closePopup() {
        val shouldReload = reloadAfterPopup
        popupTitle = null
        popupMessage = null
        popupSuccess = false
        reloadAfterPopup = false
        if (shouldReload) {
            onImportSuccess?.invoke() ?: activity?.recreate()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            val result = GameSaveTransfer.export(context, uri, password)
            showPopup(
                title = if (result.success) "Export Complete" else "Export Failed",
                result = result
            )
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val result = GameSaveTransfer.import(context, uri, password)
            showPopup(
                title = if (result.success) "Save Imported" else "Import Failed",
                result = result,
                reloadOnSuccess = true
            )
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Text("🔐 Encrypted save system", fontWeight = FontWeight.Black)
            Text(
                "Encrypted backups include your lowercase Puppy Clicker username and protected game data.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.size(9.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = PuppyPlayerIdentity.normalizeUsername(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Player username") },
                supportingText = { Text("Stored lowercase. Letters, numbers, _, - and . only.") },
                singleLine = true
            )
            Spacer(Modifier.size(6.dp))
            OutlinedButton(
                onClick = {
                    username = PuppyPlayerIdentity.setUsername(context, username)
                    PupEyeSaveGuard.seal(
                        context,
                        context.getSharedPreferences(PuppyClickerV6ViewModel.PREFS_NAME, Context.MODE_PRIVATE)
                    )
                    showPopup(
                        title = "Username Updated",
                        result = SaveTransferResult(true, "Player username saved as $username.")
                    )
                },
                enabled = PuppyPlayerIdentity.normalizeUsername(username).isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save player username")
            }

            Spacer(Modifier.size(9.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.take(128) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Backup password") },
                placeholder = { Text("Enter backup password") },
                supportingText = { Text("8+ characters. The password is never stored in the save file.") },
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
            Spacer(Modifier.size(9.dp))
            Row(Modifier.fillMaxWidth()) {
                Button(
                    onClick = { exportLauncher.launch(GameSaveTransfer.suggestedFileName(context)) },
                    enabled = password.length >= 8,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Export encrypted")
                }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/octet-stream", "application/json", "*/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Import save")
                }
            }
            Spacer(Modifier.size(7.dp))
            Text(
                "Automatic Android/data saves are device-bound by Android Keystore. Portable v3 backups require the password and reject a different configured username.",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                "No IMEI, serial number, Android ID, phone number, or account token is stored.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }

    val dialogTitle = popupTitle
    val dialogMessage = popupMessage
    if (dialogTitle != null && dialogMessage != null) {
        AlertDialog(
            onDismissRequest = ::closePopup,
            title = {
                Text(
                    dialogTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
            },
            text = {
                Column {
                    Text(
                        dialogMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (popupSuccess) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    if (!popupSuccess && dialogTitle == "Import Failed") {
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "Check the backup password and make sure the selected file is an unmodified Puppy Clicker save.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = ::closePopup) {
                    Text(if (reloadAfterPopup) "Reload Puppy Clicker" else "OK")
                }
            }
        )
    }
}
