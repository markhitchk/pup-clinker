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
 * Fully offline Puppy Code catalogue.
 *
 * Plain-text promo codes are intentionally not stored in the APK. The normalized
 * user input is salted and SHA-256 hashed, then matched against this local table.
 * Upgrade Tickets and prestige points are deliberately excluded from codes.
 * Seasonal puppies are earned through their event claims, never Puppy Codes.
 */
object LocalRedeemCodes {
    private const val SALT = "PUPPY_CLICKER_LOCAL_2026_V1|"

    private val rewards = mapOf(
        // Existing Puppy Clicker local codes.
        "7479bfe9cd2300a69ee03f08099a65c4b1584102df720332ab129dbe1197186c" to LocalRedeemReward(
            id = "buddy_hello_2026", treats = 750, message = "Buddy says hello! +750 treats."
        ),
        "6a034a81325644219d0521630bbe7586ba6232b8bf30f2682d3fba0a348de5aa" to LocalRedeemReward(
            id = "paw_pass_2026", treats = 1_000, message = "Paw Pass redeemed! +1,000 treats."
        ),
        "3c7f2f76a3310dbcbc787ca9be87a6530d7020a66dfb67426277782b4a6c" to LocalRedeemReward(
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
            message = "Legacy Shop code converted to +4,000 treats. Tickets still come from taps."
        ),

        // 2026 treat codes requested for the expanded game.
        "aa17ce8fbf9375841fd75ae90703a84bbeaeea589fd86d21065d72cdab9c043f" to LocalRedeemReward(
            id = "good_boy_26", treats = 1_000, message = "GOOD BOY! +1,000 treats."
        ),
        "296e559c14995f94e03a169998aa1897a65140b9b573fbcbb78c9fca03f824b4" to LocalRedeemReward(
            id = "boop_the_pup", treats = 750, message = "Boop successful. +750 treats."
        ),
        "7038f93210d3087d5a7ce6ce42396cc99984570e637edd275940f4ba6e490805" to LocalRedeemReward(
            id = "dog_park_day", treats = 2_000, message = "Dog Park Day! +2,000 treats."
        ),
        "f975160dd567ceef544342259284be24b76de2c95528ce03df7cbfca221d32ba" to LocalRedeemReward(
            id = "puppy_party_26", treats = 2_500, message = "Puppy Party! +2,500 treats."
        ),
        "38ab38ee32967937679fb23125a0471b84829fe5bb282e61c8ea5275fca11fb4" to LocalRedeemReward(
            id = "big_treat_bag", treats = 5_000, message = "Big Treat Bag opened! +5,000 treats."
        ),
        "ddb40bb477b723a258f29d52c013a71e30801612edc51baa6ebd892021e7b5e2" to LocalRedeemReward(
            id = "og_puppy", treats = 7_500, message = "OG Puppy bonus! +7,500 treats."
        ),
        "48a7fef7e5ee83e2b4b782220bf4e81f9ce7efa8d77ddb0e95f6e44e9b607535" to LocalRedeemReward(
            id = "thank_you_pups", treats = 10_000, message = "Thank you, pups! +10,000 treats."
        ),
        "6ee7c0b55cb72470dd834f94035f191088d51719875abb2e0e8e6bc928fcbcd6" to LocalRedeemReward(
            id = "one_more_treat", treats = 250, message = "Okay... ONE more treat."
        ),
        "f05571f68faa9579c14922dd78b62657247ee502bb0f02717ec8faafd0540698" to LocalRedeemReward(
            id = "who_ate_the_treats", treats = 1, message = "Mystery solved. You found exactly 1 treat."
        ),
        "777043e801354b065123db2f26d4b8320039fdcb2fb8714bc3431e719cb1b480" to LocalRedeemReward(
            id = "very_good_pup", treats = 4_000, message = "VERY good pup! +4,000 treats."
        ),

        // V1 character unlock codes. Seasonal puppies use the event collection instead.
        "32b5b00ec5412ba5d3e939dfc9eecf2ce163db5cb004096da95a391753f49469" to LocalRedeemReward(
            id = "aurora_pup", treats = 500, puppyId = "aurora",
            message = "Aurora unlocked +500 treats."
        ),
        "b424e3b4a21a52fcca0976118631e08b8dc95e6a25a26548e4fc542b652bcbf3" to LocalRedeemReward(
            id = "cocoa_cuddles", treats = 500, puppyId = "cocoa",
            message = "Cocoa unlocked +500 treats."
        ),
        "f8c145053f3623125b58b65c5f17a5af55fab6ac3df02dc33db02" to LocalRedeemReward(
            id = "snowball_26", treats = 750, puppyId = "snowball",
            message = "Snowball unlocked +750 treats."
        ),
        "7ac886c0c61146b74b431bb6f384cbc6a758259c6498c809d137654ac2007860" to LocalRedeemReward(
            id = "galaxy_pup", treats = 1_000, puppyId = "galaxy",
            message = "Galaxy Pup unlocked +1,000 treats."
        ),
        "b7fe2f5ab323b4d9f5d1a176d28581703bdfac2240d7a0dec2fc4b185c1ef58f" to LocalRedeemReward(
            id = "neon_buddy", puppyId = "neon_buddy",
            message = "Neon Buddy unlocked!"
        ),
        "6a9996821524ca459ea88289aa945723c96139e823dacdf0d9d945470a965135" to LocalRedeemReward(
            id = "golden_night", puppyId = "golden_night",
            message = "Golden Night unlocked!"
        ),
        "2e4044740d61546f6f2a92793235bdbc03889cb8048caa672deca2c525215747" to LocalRedeemReward(
            id = "dev_pup_26", puppyId = "dev_pup",
            message = "Dev Pup unlocked!"
        ),
        "d700478fe4f3cda1f6120cf59e7f08742d7ee760949671ea60b52aca09d6efd0" to LocalRedeemReward(
            id = "secret_snoot", puppyId = "secret_snoot",
            message = "Secret Snoot discovered!"
        ),
        "cce8b78471c3a6ef1ae5d2e911bfb6c51c868adb3d021173952bc501e119301a" to LocalRedeemReward(
            id = "classic_forever", puppyId = "classic_forever",
            message = "Classic Forever unlocked — original Puppy Clicker forever."
        ),

        // V2 character unlock codes. V2 uses new names and separate v2_ save IDs.
        "78a9f1c7a2c13c59c739c112f6acd0856d410788139e02975c7315fe22816aa5" to LocalRedeemReward(
            id = "v2_frost_code", treats = 500, puppyId = "v2_frost",
            message = "V2 Frost unlocked +500 treats."
        ),
        "d49990ca1f097a64a665898af7f011fc6432c1bb8749fd24402399ded6dd2d1e" to LocalRedeemReward(
            id = "v2_honey_code", treats = 500, puppyId = "v2_honey",
            message = "V2 Honey unlocked +500 treats."
        ),
        "567f812bfafb3f3c340d90606cbe67266c96e59b4ff7e9e7984e0c5db9dab512" to LocalRedeemReward(
            id = "v2_biscuit_code", treats = 500, puppyId = "v2_biscuit",
            message = "V2 Biscuit unlocked +500 treats."
        ),
        "aca60e394b483e8dc009ddca2ff1cb5cc53675ed9335e8cddae8ac1104a58370" to LocalRedeemReward(
            id = "v2_onyx_code", treats = 750, puppyId = "v2_onyx",
            message = "V2 Onyx unlocked +750 treats."
        ),
        "bbdfc1541fa8f336f711e6e0218caa8dde577fcd493cf67d227d4af3f993e36f" to LocalRedeemReward(
            id = "v2_domino_code", treats = 750, puppyId = "v2_domino",
            message = "V2 Domino unlocked +750 treats."
        ),
        "4d39730452fce3c022a5e1a47f509ba129cee2b244249ab4ee1ab07de12a1505" to LocalRedeemReward(
            id = "v2_chestnut_code", treats = 500, puppyId = "v2_chestnut",
            message = "V2 Chestnut unlocked +500 treats."
        ),
        "6054e4ddf0904b42ee050cd5f2db42dd6544d664c92b5f9bc796b231d642fe15" to LocalRedeemReward(
            id = "v2_prism_code", treats = 1_000, puppyId = "v2_prism",
            message = "V2 Prism unlocked +1,000 treats."
        ),
        "0e758fdc60de866c674c25cd335bdcea712d0f5c6a18815398941a641070b8f4" to LocalRedeemReward(
            id = "v2_flurry_code", treats = 750, puppyId = "v2_flurry",
            message = "V2 Flurry unlocked +750 treats."
        )
    )

    fun find(rawCode: String): LocalRedeemReward? {
        val normalized = rawCode.trim()
            .uppercase(Locale.US)
            .replace(Regex("\\s+"), "")
        if (normalized.length !in 6..64) return null
        return rewards[sha256(SALT + normalized)]?.takeUnless { reward ->
            reward.puppyId?.let(SeasonalPuppyEvents::isSeasonal) == true
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
