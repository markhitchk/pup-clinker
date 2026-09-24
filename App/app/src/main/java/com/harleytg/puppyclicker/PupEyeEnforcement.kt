package com.harleytg.puppyclicker

import java.time.Instant
import org.json.JSONObject

internal enum class PupEyeEnforcementMode {
    ALLOWED,
    REVIEW_REQUIRED,
    GLOBAL_BANNED
}

internal data class PupEyeGlobalBanInfo(
    val id: String,
    val kind: String,
    val reasonCode: String,
    val publicReason: String,
    val issuedAtEpochMs: Long,
    val expiresAtEpochMs: Long?,
    val deviceWide: Boolean
)

internal data class PupEyeEnforcementSnapshot(
    val mode: PupEyeEnforcementMode,
    val ban: PupEyeGlobalBanInfo? = null,
    val reviewMessage: String? = null,
    val lastServerVerifiedAtMs: Long = 0L
) {
    @Suppress("UNUSED_PARAMETER")
    fun blocksApp(nowMs: Long): Boolean =
        mode == PupEyeEnforcementMode.GLOBAL_BANNED ||
            mode == PupEyeEnforcementMode.REVIEW_REQUIRED

    fun toJson(): JSONObject = JSONObject().apply {
        put("mode", mode.name)
        put("lastServerVerifiedAtMs", lastServerVerifiedAtMs)
        reviewMessage?.let { put("reviewMessage", it) }
        ban?.let { info ->
            put(
                "ban",
                JSONObject().apply {
                    put("id", info.id)
                    put("kind", info.kind)
                    put("reasonCode", info.reasonCode)
                    put("publicReason", info.publicReason)
                    put("issuedAtEpochMs", info.issuedAtEpochMs)
                    if (info.expiresAtEpochMs == null) {
                        put("expiresAtEpochMs", JSONObject.NULL)
                    } else {
                        put("expiresAtEpochMs", info.expiresAtEpochMs)
                    }
                    put("deviceWide", info.deviceWide)
                }
            )
        }
    }

    companion object {
        fun allowed(lastServerVerifiedAtMs: Long = 0L): PupEyeEnforcementSnapshot =
            PupEyeEnforcementSnapshot(
                mode = PupEyeEnforcementMode.ALLOWED,
                lastServerVerifiedAtMs = lastServerVerifiedAtMs
            )

        fun fromJson(json: JSONObject): PupEyeEnforcementSnapshot {
            val mode = runCatching {
                PupEyeEnforcementMode.valueOf(json.optString("mode", "ALLOWED"))
            }.getOrDefault(PupEyeEnforcementMode.ALLOWED)
            val ban = json.optJSONObject("ban")?.let { banJson ->
                PupEyeGlobalBanInfo(
                    id = banJson.optString("id"),
                    kind = banJson.optString("kind"),
                    reasonCode = banJson.optString("reasonCode"),
                    publicReason = banJson.optString("publicReason"),
                    issuedAtEpochMs = banJson.optLong("issuedAtEpochMs", 0L),
                    expiresAtEpochMs = if (
                        banJson.has("expiresAtEpochMs") &&
                        !banJson.isNull("expiresAtEpochMs")
                    ) {
                        banJson.optLong("expiresAtEpochMs")
                    } else {
                        null
                    },
                    deviceWide = banJson.optBoolean("deviceWide", false)
                )
            }
            return PupEyeEnforcementSnapshot(
                mode = mode,
                ban = ban,
                reviewMessage = json.optString("reviewMessage")
                    .takeIf { it.isNotBlank() && it != "null" },
                lastServerVerifiedAtMs = json.optLong("lastServerVerifiedAtMs", 0L)
                    .coerceAtLeast(0L)
            )
        }

        fun fromServerResponse(
            body: JSONObject,
            verifiedAtMs: Long
        ): PupEyeEnforcementSnapshot {
            return when (body.optString("code")) {
                "GLOBAL_BANNED" -> {
                    val banJson = body.getJSONObject("ban")
                    PupEyeEnforcementSnapshot(
                        mode = PupEyeEnforcementMode.GLOBAL_BANNED,
                        ban = PupEyeGlobalBanInfo(
                            id = banJson.getString("id"),
                            kind = banJson.getString("kind"),
                            reasonCode = banJson.optString("reasonCode"),
                            publicReason = banJson.optString("publicReason"),
                            issuedAtEpochMs = parseIsoEpochMs(banJson.optString("issuedAt")),
                            expiresAtEpochMs = banJson.optString("expiresAt")
                                .takeIf { it.isNotBlank() && it != "null" }
                                ?.let(::parseIsoEpochMs),
                            deviceWide = banJson.optBoolean("deviceWide", false)
                        ),
                        lastServerVerifiedAtMs = verifiedAtMs.coerceAtLeast(0L)
                    )
                }

                "REVIEW_REQUIRED" -> PupEyeEnforcementSnapshot(
                    mode = PupEyeEnforcementMode.REVIEW_REQUIRED,
                    reviewMessage = body.optString("message")
                        .takeIf { it.isNotBlank() }
                        ?: "PupEye requires Support review.",
                    lastServerVerifiedAtMs = verifiedAtMs.coerceAtLeast(0L)
                )

                else -> allowed(verifiedAtMs.coerceAtLeast(0L))
            }
        }

        private fun parseIsoEpochMs(value: String): Long =
            runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
    }
}
