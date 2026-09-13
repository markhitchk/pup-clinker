package com.harleytg.puppyclicker

enum class PuppyOnboardingStep(val persistedIndex: Int) {
    WELCOME(0),
    PLAYER_SETUP(1),
    PERSONALIZE(2),
    PRIVACY(3),
    NOTIFICATIONS(4),
    READY(5);

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

/**
 * v4 inserts Permissions & Privacy before Notifications. Any unfinished v3 session that had
 * reached Notifications or Ready is routed through Privacy first so consent is never implied.
 */
fun migrateV3OnboardingStepToV4(oldStep: Int): Int = when (oldStep.coerceIn(0, 4)) {
    0 -> PuppyOnboardingStep.WELCOME.persistedIndex
    1 -> PuppyOnboardingStep.PLAYER_SETUP.persistedIndex
    2 -> PuppyOnboardingStep.PERSONALIZE.persistedIndex
    else -> PuppyOnboardingStep.PRIVACY.persistedIndex
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
