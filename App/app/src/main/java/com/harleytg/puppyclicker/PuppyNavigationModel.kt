package com.harleytg.puppyclicker

enum class PuppyMainDestination(val label: String, val emoji: String) {
    PLAY("Play", "🐾"),
    CARE("Care", "💖"),
    ROSTER("Roster", "🐶"),
    SHOP("Shop", "🛍️"),
    REWARDS("Rewards", "🎁")
}

enum class PuppyInternalDestination(val label: String) {
    SETTINGS("Settings"),
    PRESTIGE("Prestige")
}
