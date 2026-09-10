package com.harleytg.puppyclicker

import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeParseException
import org.json.JSONArray
import org.json.JSONObject

enum class PuppyCodeStatus { ACTIVE, EXPIRED, DISABLED, REVOKED }
enum class PuppyCodeRarity { STANDARD, RARE, SPECIAL }

data class PuppyCodeCampaign(
    val id: String,
    val title: String,
    val description: String = "",
    val badge: String? = null,
    val artworkUrl: String? = null
)

sealed class PuppyCodeReward {
    data class Treats(val amount: Long) : PuppyCodeReward()
    data class Puppy(val puppyId: String) : PuppyCodeReward()
    data class UpgradeTickets(val rarity: TicketRarity, val amount: Int) : PuppyCodeReward()
    data class Cosmetic(val id: String) : PuppyCodeReward()
    data class Badge(val id: String) : PuppyCodeReward()
    data class Boost(val id: String, val durationSeconds: Long) : PuppyCodeReward()
    data class Unknown(val type: String) : PuppyCodeReward()
}

data class PuppyCodeDefinition(
    val id: String,
    val hash: String,
    val status: PuppyCodeStatus,
    val rarity: PuppyCodeRarity? = null,
    val flags: Set<String> = emptySet(),
    val minVersionCode: Int? = null,
    val maxVersionCode: Int? = null,
    val requiredRewardSchema: Int = 1,
    val startsAtMs: Long? = null,
    val expiresAtMs: Long? = null,
    val campaign: PuppyCodeCampaign? = null,
    val rewards: List<PuppyCodeReward>,
    val message: String
)

data class PuppyCodeCatalogSnapshot(
    val revision: String,
    val codesByHash: Map<String, PuppyCodeDefinition>
)

enum class PuppyCodeRejectReason {
    INVALID,
    ALREADY_REDEEMED,
    EXPIRED,
    DISABLED,
    NOT_STARTED,
    VERSION,
    REWARD_SCHEMA,
    UNSUPPORTED_FLAG,
    UNSUPPORTED_REWARD,
    NO_AUTHORITATIVE_TIME,
    CHANNEL
}

sealed class PuppyCodeValidationResult {
    data class Valid(val definition: PuppyCodeDefinition) : PuppyCodeValidationResult()
    data class Rejected(val reason: PuppyCodeRejectReason, val message: String) : PuppyCodeValidationResult()
}

object PuppyCodeCatalog {
    const val SCHEMA = 2
    const val SUPPORTED_REWARD_SCHEMA = 1
    private const val SALT = "PUPPY_CLICKER_LOCAL_2026_V1|"
    private const val MAX_CODES = 1_000
    private const val MAX_REWARDS_PER_CODE = 32
    private const val MAX_TREATS_PER_REWARD = 10_000_000L
    private const val MAX_TICKETS_PER_REWARD = 100
    private const val MAX_BOOST_SECONDS = 7L * 24L * 60L * 60L
    private val hashRegex = Regex("[0-9a-f]{64}")
    private val idRegex = Regex("[A-Za-z0-9_.-]{1,96}")

    val supportedFlags: Set<String> = setOf(
        "LIMITED_TIME",
        "HIDDEN_PROMO",
        "SPECIAL_REVEAL",
        "REQUIRES_ONLINE",
        "REQUIRES_NEWER_VERSION",
        "DEV_ONLY",
        "STABLE_ONLY",
        "EVENT_CODE",
        "PATREON_CODE",
        "BUG_BOUNTY",
        "STAFF_CODE",
        "NO_HISTORY_DETAILS"
    )

    /** Hashes exactly what the player entered. No trim/case/spacing/hyphen normalization. */
    fun hashExact(rawCode: String): String = sha256(SALT + rawCode)

    fun findExact(rawCode: String, snapshot: PuppyCodeCatalogSnapshot): PuppyCodeDefinition? =
        snapshot.codesByHash[hashExact(rawCode)]

    @Throws(IOException::class)
    fun parse(text: String): PuppyCodeCatalogSnapshot {
        val root = JSONObject(text)
        if (root.optInt("schema", -1) != SCHEMA) throw IOException("Unsupported Puppy Code catalogue schema")
        val revision = root.optString("revision", "").trim()
        if (revision.isEmpty() || revision.length > 96) throw IOException("Invalid Puppy Code catalogue revision")

        val entries = root.getJSONArray("rewards")
        if (entries.length() > MAX_CODES) throw IOException("Too many Puppy Codes")
        val byHash = LinkedHashMap<String, PuppyCodeDefinition>(entries.length())
        val ids = HashSet<String>()

        for (index in 0 until entries.length()) {
            val item = entries.getJSONObject(index)
            val id = item.getString("id")
            if (!idRegex.matches(id)) throw IOException("Invalid Puppy Code id at index $index")
            if (!ids.add(id)) throw IOException("Duplicate Puppy Code id: $id")

            val hash = item.getString("hash").lowercase()
            if (!hashRegex.matches(hash)) throw IOException("Invalid Puppy Code hash at index $index")
            if (byHash.containsKey(hash)) throw IOException("Duplicate Puppy Code hash")

            val status = when (item.optString("status", "active").lowercase()) {
                "active" -> PuppyCodeStatus.ACTIVE
                "expired" -> PuppyCodeStatus.EXPIRED
                "disabled" -> PuppyCodeStatus.DISABLED
                "revoked" -> PuppyCodeStatus.REVOKED
                else -> throw IOException("Invalid Puppy Code status at index $index")
            }
            val rarity = if (!item.has("rarity") || item.isNull("rarity")) null else when (item.getString("rarity").lowercase()) {
                "standard" -> PuppyCodeRarity.STANDARD
                "rare" -> PuppyCodeRarity.RARE
                "special" -> PuppyCodeRarity.SPECIAL
                else -> throw IOException("Invalid Puppy Code rarity at index $index")
            }

            val flags = linkedSetOf<String>()
            val flagArray = item.optJSONArray("flags") ?: JSONArray()
            for (flagIndex in 0 until flagArray.length()) {
                val flag = flagArray.getString(flagIndex).trim()
                if (flag.isEmpty() || flag.length > 64) throw IOException("Invalid Puppy Code flag at index $index")
                flags += flag
            }

            val rewardsArray = item.getJSONArray("rewards")
            if (rewardsArray.length() !in 1..MAX_REWARDS_PER_CODE) throw IOException("Invalid Puppy Code reward count at index $index")
            val rewards = ArrayList<PuppyCodeReward>(rewardsArray.length())
            for (rewardIndex in 0 until rewardsArray.length()) {
                rewards += parseReward(rewardsArray.getJSONObject(rewardIndex), index, rewardIndex)
            }

            val message = item.getString("message").trim()
            if (message.isEmpty() || message.length > 240) throw IOException("Invalid Puppy Code message at index $index")

            val minVersionCode = optionalPositiveInt(item, "minVersionCode", index)
            val maxVersionCode = optionalPositiveInt(item, "maxVersionCode", index)
            if (minVersionCode != null && maxVersionCode != null && minVersionCode > maxVersionCode) {
                throw IOException("Invalid Puppy Code version range at index $index")
            }
            val requiredRewardSchema = item.optInt("requiredRewardSchema", 1)
            if (requiredRewardSchema <= 0) throw IOException("Invalid reward schema at index $index")

            val startsAtMs = optionalInstant(item, "startsAt", index)
            val expiresAtMs = optionalInstant(item, "expiresAt", index)
            if (startsAtMs != null && expiresAtMs != null && startsAtMs >= expiresAtMs) {
                throw IOException("Invalid Puppy Code time window at index $index")
            }

            val campaign = if (!item.has("campaign") || item.isNull("campaign")) null else parseCampaign(item.getJSONObject("campaign"), index)

            byHash[hash] = PuppyCodeDefinition(
                id = id,
                hash = hash,
                status = status,
                rarity = rarity,
                flags = flags,
                minVersionCode = minVersionCode,
                maxVersionCode = maxVersionCode,
                requiredRewardSchema = requiredRewardSchema,
                startsAtMs = startsAtMs,
                expiresAtMs = expiresAtMs,
                campaign = campaign,
                rewards = rewards,
                message = message
            )
        }

        return PuppyCodeCatalogSnapshot(revision = revision, codesByHash = byHash.toMap())
    }

    private fun parseReward(item: JSONObject, codeIndex: Int, rewardIndex: Int): PuppyCodeReward {
        return when (val type = item.getString("type").trim().lowercase()) {
            "treats" -> {
                val amount = item.getLong("amount")
                if (amount !in 1..MAX_TREATS_PER_REWARD) throw IOException("Invalid Treats reward at $codeIndex:$rewardIndex")
                PuppyCodeReward.Treats(amount)
            }
            "puppy" -> {
                val puppyId = item.getString("puppyId").trim()
                if (!idRegex.matches(puppyId)) throw IOException("Invalid puppy reward at $codeIndex:$rewardIndex")
                PuppyCodeReward.Puppy(puppyId)
            }
            "upgrade_ticket" -> {
                val rarity = try {
                    TicketRarity.valueOf(item.getString("rarity").uppercase())
                } catch (_: IllegalArgumentException) {
                    throw IOException("Invalid ticket rarity at $codeIndex:$rewardIndex")
                }
                val amount = item.getInt("amount")
                if (amount !in 1..MAX_TICKETS_PER_REWARD) throw IOException("Invalid ticket amount at $codeIndex:$rewardIndex")
                PuppyCodeReward.UpgradeTickets(rarity, amount)
            }
            "cosmetic" -> PuppyCodeReward.Cosmetic(requireRewardId(item, "id", codeIndex, rewardIndex))
            "badge" -> PuppyCodeReward.Badge(requireRewardId(item, "id", codeIndex, rewardIndex))
            "boost" -> {
                val id = requireRewardId(item, "id", codeIndex, rewardIndex)
                val duration = item.getLong("durationSeconds")
                if (duration !in 1..MAX_BOOST_SECONDS) throw IOException("Invalid boost duration at $codeIndex:$rewardIndex")
                PuppyCodeReward.Boost(id, duration)
            }
            else -> PuppyCodeReward.Unknown(type)
        }
    }

    private fun requireRewardId(item: JSONObject, key: String, codeIndex: Int, rewardIndex: Int): String {
        val value = item.getString(key).trim()
        if (!idRegex.matches(value)) throw IOException("Invalid reward id at $codeIndex:$rewardIndex")
        return value
    }

    private fun optionalPositiveInt(item: JSONObject, key: String, index: Int): Int? {
        if (!item.has(key) || item.isNull(key)) return null
        val value = item.getInt(key)
        if (value <= 0) throw IOException("Invalid $key at index $index")
        return value
    }

    private fun optionalInstant(item: JSONObject, key: String, index: Int): Long? {
        if (!item.has(key) || item.isNull(key)) return null
        return try {
            Instant.parse(item.getString(key)).toEpochMilli()
        } catch (_: DateTimeParseException) {
            throw IOException("Invalid $key at index $index")
        }
    }

    private fun parseCampaign(item: JSONObject, index: Int): PuppyCodeCampaign {
        val id = item.getString("id").trim()
        val title = item.getString("title").trim()
        val description = item.optString("description", "").trim()
        val badge = item.optString("badge", "").trim().ifEmpty { null }
        val artworkUrl = item.optString("artworkUrl", "").trim().ifEmpty { null }
        if (!idRegex.matches(id) || title.isEmpty() || title.length > 80 || description.length > 240) {
            throw IOException("Invalid campaign metadata at index $index")
        }
        return PuppyCodeCampaign(id, title, description, badge, artworkUrl)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

object PuppyCodeValidator {
    fun validate(
        definition: PuppyCodeDefinition,
        versionCode: Int,
        redeemedIds: Set<String>,
        authoritativeTimeMs: Long?,
        channel: String,
        supportedRewardSchema: Int = PuppyCodeCatalog.SUPPORTED_REWARD_SCHEMA
    ): PuppyCodeValidationResult {
        if (definition.id in redeemedIds) {
            return reject(PuppyCodeRejectReason.ALREADY_REDEEMED, "This Puppy Code was already redeemed.")
        }

        when (definition.status) {
            PuppyCodeStatus.EXPIRED -> return reject(PuppyCodeRejectReason.EXPIRED, "This Puppy Code has expired.")
            PuppyCodeStatus.DISABLED -> return reject(PuppyCodeRejectReason.DISABLED, "This Puppy Code is currently unavailable.")
            PuppyCodeStatus.REVOKED -> return reject(PuppyCodeRejectReason.INVALID, "Invalid Puppy Code.")
            PuppyCodeStatus.ACTIVE -> Unit
        }

        val unknownFlags = definition.flags - PuppyCodeCatalog.supportedFlags
        if (unknownFlags.isNotEmpty() || "NO_REWARD_PREVIEW" in definition.flags) {
            return reject(
                PuppyCodeRejectReason.UNSUPPORTED_FLAG,
                "This Puppy Code requires a different or newer version of Puppy Clicker. Update the app and try again."
            )
        }
        if ("DEV_ONLY" in definition.flags && "STABLE_ONLY" in definition.flags) {
            return reject(PuppyCodeRejectReason.UNSUPPORTED_FLAG, "This Puppy Code has incompatible restrictions.")
        }

        if (definition.minVersionCode != null && versionCode < definition.minVersionCode) {
            return reject(PuppyCodeRejectReason.VERSION, "This Puppy Code requires a newer version of Puppy Clicker. Please update the app and try again.")
        }
        if (definition.maxVersionCode != null && versionCode > definition.maxVersionCode) {
            return reject(PuppyCodeRejectReason.VERSION, "This Puppy Code is not compatible with your version of Puppy Clicker.")
        }
        if (definition.requiredRewardSchema > supportedRewardSchema) {
            return reject(PuppyCodeRejectReason.REWARD_SCHEMA, "This Puppy Code contains rewards that this version of Puppy Clicker cannot process.")
        }

        val normalizedChannel = channel.lowercase()
        if ("DEV_ONLY" in definition.flags && normalizedChannel != "dev") {
            return reject(PuppyCodeRejectReason.CHANNEL, "This Puppy Code is not compatible with this Puppy Clicker release.")
        }
        if ("STABLE_ONLY" in definition.flags && normalizedChannel != "stable") {
            return reject(PuppyCodeRejectReason.CHANNEL, "This Puppy Code is not compatible with this Puppy Clicker release.")
        }

        if (definition.startsAtMs != null || definition.expiresAtMs != null) {
            val now = authoritativeTimeMs ?: return reject(
                PuppyCodeRejectReason.NO_AUTHORITATIVE_TIME,
                "This limited-time Puppy Code cannot be verified right now. Check your connection and try again."
            )
            if (definition.startsAtMs != null && now < definition.startsAtMs) {
                return reject(PuppyCodeRejectReason.NOT_STARTED, "This Puppy Code is not available yet.")
            }
            if (definition.expiresAtMs != null && now >= definition.expiresAtMs) {
                return reject(PuppyCodeRejectReason.EXPIRED, "This Puppy Code has expired.")
            }
        }

        if (definition.rewards.any { it is PuppyCodeReward.Unknown }) {
            return reject(PuppyCodeRejectReason.UNSUPPORTED_REWARD, "This Puppy Code contains rewards that this version of Puppy Clicker cannot process.")
        }

        return PuppyCodeValidationResult.Valid(definition)
    }

    private fun reject(reason: PuppyCodeRejectReason, message: String) =
        PuppyCodeValidationResult.Rejected(reason, message)
}
