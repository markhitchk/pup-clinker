package com.harleytg.puppyclicker

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** One manifest-backed puppy plus its exact PNG location. */
data class PuppyRosterAsset(
    val style: PuppyStyle,
    val assetId: String,
    val folder: String,
    val fileName: String,
    val free: Boolean,
    val groupId: String,
    val groupTitle: String,
    val groupOrder: Int
)

data class PuppyRosterGroup(
    val id: String,
    val title: String,
    val puppies: List<PuppyStyle>,
    val order: Int
)

/**
 * Hybrid roster registry.
 *
 * V1/V2 are permanent compatibility baselines. Test and v3+ entries are generated
 * from repository manifests at build time, then may be augmented from the live
 * repository listing when that endpoint is publicly readable. A valid live roster
 * is cached so newly streamed puppies remain available offline after first sync.
 */
internal object DynamicPuppyRoster {
    private const val TAG = "PuppyRosterStream"
    private const val CONTENTS_URL =
        "https://api.github.com/repos/markhitchk/pup-clinker/contents/assets?ref=main"
    private const val RAW_BASE =
        "https://raw.githubusercontent.com/markhitchk/pup-clinker/main/assets"
    private const val CACHE_SCHEMA = 1
    private const val MAX_INDEX_BYTES = 512 * 1024
    private const val MAX_MANIFEST_BYTES = 256 * 1024
    private const val MAX_DYNAMIC_PUPPIES = 500
    private const val REFRESH_INTERVAL_MS = 6L * 60L * 60L * 1_000L
    private const val RETRY_INTERVAL_MS = 5L * 60L * 1_000L
    private const val LOOP_INTERVAL_MS = 60L * 60L * 1_000L
    private val folderRegex = Regex("v[3-9][0-9]*")
    private val idRegex = Regex("[a-z0-9_]{1,64}")
    private val fileRegex = Regex("[A-Za-z0-9._-]{1,128}\\.png")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    private val legacyAssets: List<PuppyRosterAsset> by lazy {
        val v1 = V1_PUPPY_STYLES.map { style ->
            PuppyRosterAsset(
                style = style,
                assetId = "v1_${style.id}",
                folder = "v1",
                fileName = "v1_${style.id}.png",
                free = !style.redeemOnly,
                groupId = "v1",
                groupTitle = "V1 Puppies",
                groupOrder = 1
            )
        }
        val v2 = V2_PUPPY_STYLES.map { style ->
            PuppyRosterAsset(
                style = style,
                assetId = style.id,
                folder = "v2",
                fileName = "${style.id}.png",
                free = !style.redeemOnly,
                groupId = "v2",
                groupTitle = "V2 Puppies",
                groupOrder = 2
            )
        }
        v1 + v2
    }

    private val bundledDynamicAssets: List<PuppyRosterAsset> by lazy { EXTRA_PUPPY_DESCRIPTORS }

    @Volatile
    private var assetsByStyleId: Map<String, PuppyRosterAsset> = buildAssetMap(emptyList())

    @Volatile
    private var assetsByAssetId: Map<String, PuppyRosterAsset> =
        assetsByStyleId.values.associateBy { it.assetId }

    private val _groups = MutableStateFlow(groupsFrom(assetsByStyleId.values.toList()))
    val groups: StateFlow<List<PuppyRosterGroup>> = _groups.asStateFlow()

    fun initialize(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        scope.launch {
            loadCache(app)?.let(::publishRemote)
            while (true) {
                refresh(app)
                delay(LOOP_INTERVAL_MS)
            }
        }
    }

    fun style(styleId: String): PuppyStyle? = assetsByStyleId[styleId]?.style

    fun asset(styleId: String): PuppyRosterAsset? = assetsByStyleId[styleId]

    fun assetByAssetId(assetId: String): PuppyRosterAsset? = assetsByAssetId[assetId]

    fun isKnown(styleId: String): Boolean = styleId in assetsByStyleId

    fun freeIds(): Set<String> = assetsByStyleId.values
        .asSequence()
        .filter { it.free }
        .map { it.style.id }
        .toCollection(linkedSetOf())

    private fun buildAssetMap(remoteDynamic: List<PuppyRosterAsset>): Map<String, PuppyRosterAsset> {
        // V1/V2 remain bundled compatibility fallbacks, but a valid live V2 manifest is
        // authoritative for roster membership. This lets new V2 puppies appear without
        // waiting for a new APK while preserving the bundled list when GitHub is offline.
        val merged = LinkedHashMap<String, PuppyRosterAsset>()
        legacyAssets.forEach { merged[it.style.id] = it }
        bundledDynamicAssets.forEach { merged[it.style.id] = it }
        remoteDynamic.forEach { merged[it.style.id] = it }

        val all = merged.values.toList()
        require(all.size <= MAX_DYNAMIC_PUPPIES + legacyAssets.size) { "Too many puppy roster entries" }
        val assetIds = HashSet<String>()
        all.forEach { asset ->
            require(assetIds.add(asset.assetId)) { "Duplicate puppy asset id: ${asset.assetId}" }
        }
        return merged.toMap()
    }

    private fun publishRemote(remoteDynamic: List<PuppyRosterAsset>) {
        val next = buildAssetMap(remoteDynamic)
        assetsByStyleId = next
        assetsByAssetId = next.values.associateBy { it.assetId }
        _groups.value = groupsFrom(next.values.toList())
    }

    private fun groupsFrom(assets: List<PuppyRosterAsset>): List<PuppyRosterGroup> = assets
        .groupBy { it.groupId }
        .map { (id, items) ->
            val first = items.first()
            PuppyRosterGroup(
                id = id,
                title = first.groupTitle,
                puppies = items.map { it.style },
                order = first.groupOrder
            )
        }
        .sortedWith(compareBy<PuppyRosterGroup> { it.order }.thenBy { it.id })

    private fun cacheFile(context: Context): File = File(context.filesDir, "dynamic-puppy-roster-v1.json")

    private fun prefs(context: Context) =
        context.getSharedPreferences("dynamic_puppy_roster_v1", Context.MODE_PRIVATE)

    private fun loadCache(context: Context): List<PuppyRosterAsset>? {
        val file = cacheFile(context)
        if (!file.isFile || file.length() !in 1..MAX_INDEX_BYTES.toLong()) return null
        return try {
            parseCache(file.readText(Charsets.UTF_8))
        } catch (error: Exception) {
            Log.w(TAG, "Ignoring invalid cached dynamic roster", error)
            null
        }
    }

    private fun refresh(context: Context) {
        val settings = prefs(context)
        val now = System.currentTimeMillis()
        val checked = settings.getLong("checked", 0L)
        val attempted = settings.getLong("attempted", 0L)
        if (now - checked in 0 until REFRESH_INTERVAL_MS) return
        if (now - attempted in 0 until RETRY_INTERVAL_MS) return
        settings.edit().putLong("attempted", now).apply()

        try {
            val directoryText = fetchText(CONTENTS_URL, MAX_INDEX_BYTES, "application/vnd.github+json")
            val directories = JSONArray(directoryText)
            val dynamicFolders = buildList {
                for (index in 0 until directories.length()) {
                    val item = directories.getJSONObject(index)
                    if (item.optString("type") != "dir") continue
                    val name = item.optString("name")
                    if (name == "v2" || name == "test" || folderRegex.matches(name)) add(name)
                }
            }.distinct().sortedWith(compareBy<String> {
                if (it == "test") Int.MAX_VALUE else it.removePrefix("v").toIntOrNull() ?: Int.MAX_VALUE - 1
            }.thenBy { it })

            val remote = ArrayList<PuppyRosterAsset>()
            for (folder in dynamicFolders) {
                try {
                    val manifest = fetchText("$RAW_BASE/$folder/manifest.json", MAX_MANIFEST_BYTES, "application/json")
                    remote += parseManifest(folder, manifest)
                    if (remote.size > MAX_DYNAMIC_PUPPIES) throw IOException("Dynamic roster exceeds puppy limit")
                } catch (error: Exception) {
                    Log.w(TAG, "Skipping invalid dynamic roster folder: $folder", error)
                }
            }

            val serialized = serializeCache(remote)
            persist(context, serialized)
            publishRemote(remote)
            settings.edit().putLong("checked", now).apply()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // The repository is currently private, so normal installs use the generated
            // manifest fallback until a public read-only endpoint is available.
            Log.w(TAG, "Using bundled/cached dynamic roster", error)
        }
    }

    internal fun parseManifest(folder: String, text: String): List<PuppyRosterAsset> {
        require(folder == "v2" || folder == "test" || folderRegex.matches(folder)) { "Unsupported dynamic folder" }
        val root = JSONObject(text)
        val puppies = root.getJSONArray("puppies")
        if (puppies.length() > MAX_DYNAMIC_PUPPIES) throw IOException("Too many dynamic puppies")
        val ownership = root.optString("ownership").lowercase()
        val roster = root.optString("roster").trim()
        val groupTitle = when {
            folder == "test" -> "Test Puppies"
            roster.isNotBlank() -> "${roster.uppercase()} Puppies"
            else -> "${folder.uppercase()} Puppies"
        }
        val groupOrder = if (folder == "test") Int.MAX_VALUE else folder.removePrefix("v").toIntOrNull() ?: Int.MAX_VALUE - 1
        val requiredPrefix = if (folder == "test") "test_" else "${folder}_"
        val seen = HashSet<String>()

        return buildList {
            for (index in 0 until puppies.length()) {
                val item = puppies.getJSONObject(index)
                val assetId = item.getString("asset_id").trim()
                if (!idRegex.matches(assetId) || !assetId.startsWith(requiredPrefix)) {
                    throw IOException("Invalid dynamic asset id at index $index")
                }
                if (!seen.add(assetId)) throw IOException("Duplicate dynamic asset id")
                val fileName = item.getString("file").trim()
                if (!fileRegex.matches(fileName)) throw IOException("Invalid dynamic PNG filename")
                val free = if (item.has("free")) item.getBoolean("free") else ownership == "free"
                val redeemOnly = if (free) false else item.optBoolean("redeem_only", true)
                val name = item.optString("name").trim().ifBlank {
                    assetId.removePrefix(requiredPrefix).split('_').joinToString(" ") { word ->
                        word.replaceFirstChar { ch -> ch.uppercase() }
                    }
                }
                if (name.isBlank() || name.length > 80) throw IOException("Invalid dynamic puppy name")
                val emoji = item.optString("emoji").trim().ifBlank { if (folder == "test") "🧪" else "🐾" }
                val description = item.optString("description").trim().ifBlank {
                    if (folder == "test") "Free test roster puppy." else "${groupTitle.removeSuffix(" Puppies")} roster puppy."
                }
                if (description.length > 240) throw IOException("Dynamic puppy description is too long")

                add(
                    PuppyRosterAsset(
                        style = PuppyStyle(assetId, name, emoji, description, redeemOnly = redeemOnly),
                        assetId = assetId,
                        folder = folder,
                        fileName = fileName,
                        free = free,
                        groupId = folder,
                        groupTitle = groupTitle,
                        groupOrder = groupOrder
                    )
                )
            }
        }
    }

    private fun serializeCache(assets: List<PuppyRosterAsset>): String {
        val root = JSONObject().put("schema", CACHE_SCHEMA)
        val array = JSONArray()
        assets.forEach { asset ->
            array.put(
                JSONObject()
                    .put("styleId", asset.style.id)
                    .put("name", asset.style.name)
                    .put("emoji", asset.style.emoji)
                    .put("description", asset.style.description)
                    .put("redeemOnly", asset.style.redeemOnly)
                    .put("assetId", asset.assetId)
                    .put("folder", asset.folder)
                    .put("fileName", asset.fileName)
                    .put("free", asset.free)
                    .put("groupId", asset.groupId)
                    .put("groupTitle", asset.groupTitle)
                    .put("groupOrder", asset.groupOrder)
            )
        }
        root.put("assets", array)
        return root.toString()
    }

    private fun parseCache(text: String): List<PuppyRosterAsset> {
        val root = JSONObject(text)
        if (root.optInt("schema", -1) != CACHE_SCHEMA) throw IOException("Unsupported dynamic roster cache")
        val array = root.getJSONArray("assets")
        if (array.length() > MAX_DYNAMIC_PUPPIES) throw IOException("Cached roster is too large")
        val result = ArrayList<PuppyRosterAsset>(array.length())
        val seen = HashSet<String>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val styleId = item.getString("styleId")
            val assetId = item.getString("assetId")
            val folder = item.getString("folder")
            val fileName = item.getString("fileName")
            if (!idRegex.matches(styleId) || styleId != assetId || !seen.add(styleId)) throw IOException("Invalid cached style")
            if (!(folder == "v2" || folder == "test" || folderRegex.matches(folder)) || !fileRegex.matches(fileName)) throw IOException("Invalid cached asset path")
            val free = item.getBoolean("free")
            val redeemOnly = if (free) false else item.getBoolean("redeemOnly")
            result += PuppyRosterAsset(
                style = PuppyStyle(
                    styleId,
                    item.getString("name"),
                    item.getString("emoji"),
                    item.getString("description"),
                    redeemOnly = redeemOnly
                ),
                assetId = assetId,
                folder = folder,
                fileName = fileName,
                free = free,
                groupId = item.getString("groupId"),
                groupTitle = item.getString("groupTitle"),
                groupOrder = item.getInt("groupOrder")
            )
        }
        return result
    }

    private fun fetchText(url: String, maxBytes: Int, accept: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("User-Agent", "PuppyClicker-Android")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Roster endpoint returned HTTP ${connection.responseCode}")
            }
            val length = connection.contentLengthLong
            if (length > maxBytes) throw IOException("Roster response exceeds size limit")
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > maxBytes) throw IOException("Roster response exceeds size limit")
                    output.write(buffer, 0, count)
                }
            }
            return output.toByteArray().toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private fun persist(context: Context, text: String) {
        val target = cacheFile(context)
        target.parentFile?.mkdirs()
        val temp = File.createTempFile("dynamic-roster", ".tmp", target.parentFile)
        try {
            temp.writeText(text, Charsets.UTF_8)
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temp.delete()
        }
    }
}
