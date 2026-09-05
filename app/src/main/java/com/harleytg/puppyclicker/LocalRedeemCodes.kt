package com.harleytg.puppyclicker

import java.security.MessageDigest
import java.util.Locale

data class LocalRedeemReward(
    val id: String,
    val treats: Long = 0,
    val tickets: Int = 0,
    val puppyId: String? = null,
    val message: String
)

/**
 * Fully offline promo-code catalogue.
 *
 * Plain-text promo codes are intentionally not stored in the APK. The normalized
 * user input is salted and SHA-256 hashed, then matched against the local table.
 * This is appropriate for an offline game, but it is not equivalent to a
 * server-authoritative redemption service: a determined reverse engineer can
 * still inspect an APK and reproduce the verification logic.
 */
object LocalRedeemCodes {
    private const val SALT = "PUPPY_CLICKER_LOCAL_2026_V1|"

    private val rewards = mapOf(
        "7479bfe9cd2300a69ee03f08099a65c4b1584102df720332ab129dbe1197186c" to LocalRedeemReward(
            id = "buddy_hello_2026", treats = 750, message = "Buddy says hello! +750 treats."
        ),
        "6a034a81325644219d0521630bbe7586ba6232b8bf30f2682d3fba0a348de5aa" to LocalRedeemReward(
            id = "paw_pass_2026", tickets = 2, message = "+2 Upgrade Tickets."
        ),
        "3c7f2f76a3310dbcbc787ca9be87a6530d7020a66df7614ea1b9bf052944f74c" to LocalRedeemReward(
            id = "pup_shop_boost", treats = 1_500, tickets = 1,
            message = "+1,500 treats and +1 Upgrade Ticket."
        ),
        "9c80a7116a0bdd3a9332f4935aa79ece71bc530c4ef44b656fdb67426277782b" to LocalRedeemReward(
            id = "midnight_moon", treats = 500, tickets = 1, puppyId = "midnight",
            message = "Midnight unlocked, +500 treats, and +1 ticket."
        ),
        "536e2496e91da7b3a56f27e2f433411ee5f70cd850da32f4515138fc07b9abed" to LocalRedeemReward(
            id = "cloud_cuddles", treats = 500, tickets = 1, puppyId = "cloud",
            message = "Cloud unlocked, +500 treats, and +1 ticket."
        ),
        "336e4312e1b0e2242e87efb9420c54779f8751485f4d5f19e3168de4867f8997" to LocalRedeemReward(
            id = "htg_puppy_2026", treats = 5_000, tickets = 3,
            message = "+5,000 treats and +3 Upgrade Tickets."
        ),
        "f19a941f5f46db9174fc6d6ebbe08be9298dc66705b939cc5a744dce0342f65d" to LocalRedeemReward(
            id = "treat_time_26", treats = 2_500, message = "+2,500 treats."
        ),
        "8d260c767564ee705b207e3aef741d0c8050a573e0d312b74be9b2bb5e907912" to LocalRedeemReward(
            id = "shop_tickets_3", tickets = 3, message = "+3 Upgrade Tickets."
        )
    )

    fun find(rawCode: String): LocalRedeemReward? {
        val normalized = rawCode.trim()
            .uppercase(Locale.US)
            .replace(Regex("\\s+"), "")
        if (normalized.length !in 6..64) return null
        return rewards[sha256(SALT + normalized)]
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
