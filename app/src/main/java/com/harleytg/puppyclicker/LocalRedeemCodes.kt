package com.harleytg.puppyclicker

import java.security.MessageDigest
import java.util.Locale

data class LocalRedeemReward(
    val id: String,
    val treats: Long = 0,
    val puppyId: String? = null,
    val message: String
)

/**
 * Fully offline promo-code catalogue.
 *
 * Plain-text promo codes are intentionally not stored in the APK. The normalized
 * user input is salted and SHA-256 hashed, then matched against the local table.
 * Upgrade Tickets are deliberately excluded: rarity tickets are earned only by
 * legitimate puppy taps.
 */
object LocalRedeemCodes {
    private const val SALT = "PUPPY_CLICKER_LOCAL_2026_V1|"

    private val rewards = mapOf(
        "7479bfe9cd2300a69ee03f08099a65c4b1584102df720332ab129dbe1197186c" to LocalRedeemReward(
            id = "buddy_hello_2026", treats = 750, message = "Buddy says hello! +750 treats."
        ),
        "6a034a81325644219d0521630bbe7586ba6232b8bf30f2682d3fba0a348de5aa" to LocalRedeemReward(
            id = "paw_pass_2026", treats = 1_000, message = "Paw Pass redeemed! +1,000 treats."
        ),
        "3c7f2f76a3310dbcbc787ca9be87a6530d7020a66df7614ea1b9bf052944f74c" to LocalRedeemReward(
            id = "pup_shop_boost", treats = 2_500, message = "Shop Boost redeemed! +2,500 treats."
        ),
        "9c80a7116a0bdd3a9332f4935aa79ece71bc530c4ef44b656fdb67426277782b" to LocalRedeemReward(
            id = "midnight_moon", treats = 500, puppyId = "midnight",
            message = "Midnight unlocked and +500 treats."
        ),
        "536e2496e91da7b3a56f27e2f433411ee5f70cd850da32f4515138fc07b9abed" to LocalRedeemReward(
            id = "cloud_cuddles", treats = 500, puppyId = "cloud",
            message = "Cloud unlocked and +500 treats."
        ),
        "336e4312e1b0e2242e87efb9420c54779f8751485f4d5f19e3168de4867f8997" to LocalRedeemReward(
            id = "htg_puppy_2026", treats = 10_000, message = "HTG Puppy bonus! +10,000 treats."
        ),
        "f19a941f5f46db9174fc6d6ebbe08be9298dc66705b939cc5a744dce0342f65d" to LocalRedeemReward(
            id = "treat_time_26", treats = 2_500, message = "+2,500 treats."
        ),
        "8d260c767564ee705b207e3aef741d0c8050a573e0d312b74be9b2bb5e907912" to LocalRedeemReward(
            id = "shop_tickets_3", treats = 4_000,
            message = "Legacy Shop code converted to +4,000 treats. Tickets now drop only from taps."
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
