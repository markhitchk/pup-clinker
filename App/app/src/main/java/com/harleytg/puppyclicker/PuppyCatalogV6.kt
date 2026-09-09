package com.harleytg.puppyclicker

/**
 * Puppy Clicker character catalogue.
 *
 * V1 keeps the original Puppy Clicker roster and special variants.
 * V2 is a separate generation with its own names and uploaded character artwork.
 * V2 names intentionally never reuse a V1 display name.
 */
val V1_PUPPY_STYLES = PUPPY_STYLES + listOf(
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

/**
 * V2 roster. These IDs are prefixed with v2_ so saves, redeem rewards and UI grouping
 * can distinguish the new generation from visually similar V1 puppies.
 */
val V2_PUPPY_STYLES = listOf(
    PuppyStyle("v2_frost", "Frost", "🧊", "V2 fluffy white puppy with bright pink ears.", redeemOnly = true),
    PuppyStyle("v2_honey", "Honey", "🍯", "V2 happy golden puppy with a cream chest and tail tip.", redeemOnly = true),
    PuppyStyle("v2_biscuit", "Biscuit", "🍪", "V2 curly apricot puppy with cream curls.", redeemOnly = true),
    PuppyStyle("v2_onyx", "Onyx", "🖤", "V2 dark navy puppy with warm cream markings.", redeemOnly = true),
    PuppyStyle("v2_domino", "Domino", "🎲", "V2 spotted Dalmatian puppy with a bold eye patch.", redeemOnly = true),
    PuppyStyle("v2_chestnut", "Chestnut", "🌰", "V2 cocoa-brown puppy with cream muzzle and paws.", redeemOnly = true),
    PuppyStyle("v2_prism", "Prism", "🔮", "V2 pastel purple, teal and cream fluffy puppy.", redeemOnly = true),
    PuppyStyle("v2_flurry", "Flurry", "🌨️", "V2 fluffy snow-white puppy with a curled tail.", redeemOnly = true)
)

val V6_PUPPY_STYLES = V1_PUPPY_STYLES + V2_PUPPY_STYLES
val V1_PUPPY_IDS: Set<String> = V1_PUPPY_STYLES.mapTo(linkedSetOf()) { it.id }
val V2_PUPPY_IDS: Set<String> = V2_PUPPY_STYLES.mapTo(linkedSetOf()) { it.id }
val V6_PUPPY_IDS: Set<String> = V6_PUPPY_STYLES.mapTo(linkedSetOf()) { it.id }
