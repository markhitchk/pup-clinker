package com.harleytg.puppyclicker

enum class PuppyOnboardingStep(val persistedIndex: Int) {
    WELCOME(0),
    PLAYER_SETUP(1),
    PERSONALIZE(2),
    NOTIFICATIONS(3),
    READY(4);

    companion object {
        fun fromPersisted(index: Int): PuppyOnboardingStep =
            entries.firstOrNull { it.persistedIndex == index } ?: WELCOME
    }
}

enum class PuppyPlayerSetupMethod {
    LOCAL,
    DISCORD,
    IMPORT_SAVE
}

data class PuppyOnboardingSessionState(
    val playerSetupMethod: PuppyPlayerSetupMethod = PuppyPlayerSetupMethod.LOCAL,
    val importedSave: Boolean = false,
    val birthdaySkipped: Boolean = false
)

enum class PuppyNotificationPermissionDecision {
    NONE,
    REQUEST
}

fun migrateLegacyOnboardingStep(oldStep: Int): Int = when (oldStep.coerceIn(0, 5)) {
    0 -> 0
    1 -> 1
    2, 3 -> 2
    4 -> 3
    else -> 4
}

fun localUsernameEligible(raw: String): Boolean {
    val normalized = PuppyPlayerIdentity.normalizeUsername(raw)
    return normalized.isNotBlank() &&
        PuppyPlayerIdentity.usernameModerationIssue(normalized) == null
}

fun localUsernameSubmissionEnabled(raw: String): Boolean =
    PuppyPlayerIdentity.normalizeUsername(raw).isNotBlank()

fun notificationPermissionDecision(
    sdkInt: Int,
    notificationsEnabled: Boolean,
    permissionGranted: Boolean
): PuppyNotificationPermissionDecision =
    if (sdkInt >= 33 && notificationsEnabled && !permissionGranted) {
        PuppyNotificationPermissionDecision.REQUEST
    } else {
        PuppyNotificationPermissionDecision.NONE
    }
