package com.harleytg.puppyclicker

/**
 * Expanded Puppy Clicker character catalogue.
 * Every variant intentionally reuses the original source_pup artwork as its base;
 * the Android UI changes fur tint, backdrop and badge/accessory treatment.
 */
val V6_PUPPY_STYLES = PUPPY_STYLES + listOf(
    PuppyStyle("aurora", "Aurora", "🌌", "Aurora-purple and teal fur glow.", redeemOnly = true),
    PuppyStyle("cocoa", "Cocoa", "🍫", "Warm cocoa-brown fur with cream highlights.", redeemOnly = true),
    PuppyStyle("snowball", "Snowball", "❄️", "Snow-white fur with an icy blue look.", redeemOnly = true),
    PuppyStyle("galaxy", "Galaxy", "🪐", "Deep violet galaxy fur with a cosmic glow.", redeemOnly = true),
    PuppyStyle("neon_buddy", "Neon Buddy", "💡", "Electric cyan-and-purple neon Buddy.", redeemOnly = true),
    PuppyStyle("golden_night", "Golden Night", "🌙", "Dark midnight fur with a rich gold treatment.", redeemOnly = true),
    PuppyStyle("halloween", "Pumpkin Pup", "🎃", "Limited Halloween orange-and-purple puppy.", redeemOnly = true),
    PuppyStyle("santa", "Santa Paws", "🎅", "Limited Christmas red-and-snow puppy.", redeemOnly = true),
    PuppyStyle("birthday", "Birthday Buddy", "🎂", "Birthday party puppy with a bright celebration look.", redeemOnly = true),
    PuppyStyle("dev_pup", "Dev Pup", "🛠️", "Developer-themed Puppy Clicker variant.", redeemOnly = true),
    PuppyStyle("secret_snoot", "Secret Snoot", "🤫", "A hidden mystery puppy for code hunters.", redeemOnly = true),
    PuppyStyle("classic_forever", "Classic Forever", "🕹️", "A special tribute variant of the original Puppy Clicker pup.", redeemOnly = true)
)

val V6_PUPPY_IDS: Set<String> = V6_PUPPY_STYLES.mapTo(linkedSetOf()) { it.id }
