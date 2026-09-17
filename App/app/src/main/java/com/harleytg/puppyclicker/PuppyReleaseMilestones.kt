package com.harleytg.puppyclicker

data class PuppyReleaseMilestoneResult(
    val claims: Set<String>,
    val badges: Set<String>,
    val applied: Boolean
)

internal object PuppyReleaseMilestones {
    const val RELEASE_1_0_CLAIM_ID = "release_1_0_launch_reward"
    const val RELEASE_1_0_BADGE_ID = "release_1_0_badge"
    const val RELEASE_1_0_BADGE_NAME = "Puppy Clicker 1.0"

    fun claimOnePointZero(
        claims: Set<String>,
        badges: Set<String>
    ): PuppyReleaseMilestoneResult {
        if (RELEASE_1_0_CLAIM_ID in claims && RELEASE_1_0_BADGE_ID in badges) {
            return PuppyReleaseMilestoneResult(claims, badges, false)
        }
        return PuppyReleaseMilestoneResult(
            claims = claims + RELEASE_1_0_CLAIM_ID,
            badges = badges + RELEASE_1_0_BADGE_ID,
            applied = true
        )
    }
}
