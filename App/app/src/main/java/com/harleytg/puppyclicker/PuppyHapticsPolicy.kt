package com.harleytg.puppyclicker

enum class PuppyHapticEvent {
    TAP,
    SELECTION,
    NAVIGATION,
    TOGGLE,
    SUCCESS,
    ERROR,
    REWARD,
    TICKET_DROP,
    DANGER_CONFIRM,
    TEST
}

enum class PuppyHapticRoute {
    NONE,
    SEMANTIC_THEN_DIRECT,
    DIRECT
}

enum class PuppyHapticStrength {
    LIGHT,
    MEDIUM,
    STRONG
}

data class PuppyHapticPlan(
    val route: PuppyHapticRoute,
    val strength: PuppyHapticStrength
)

object PuppyHapticPolicy {
    fun plan(event: PuppyHapticEvent, enabled: Boolean): PuppyHapticPlan {
        if (!enabled) {
            return PuppyHapticPlan(PuppyHapticRoute.NONE, PuppyHapticStrength.LIGHT)
        }

        return when (event) {
            PuppyHapticEvent.TAP,
            PuppyHapticEvent.SELECTION,
            PuppyHapticEvent.NAVIGATION,
            PuppyHapticEvent.TOGGLE ->
                PuppyHapticPlan(PuppyHapticRoute.SEMANTIC_THEN_DIRECT, PuppyHapticStrength.LIGHT)

            PuppyHapticEvent.SUCCESS ->
                PuppyHapticPlan(PuppyHapticRoute.SEMANTIC_THEN_DIRECT, PuppyHapticStrength.MEDIUM)

            PuppyHapticEvent.ERROR ->
                PuppyHapticPlan(PuppyHapticRoute.SEMANTIC_THEN_DIRECT, PuppyHapticStrength.STRONG)

            PuppyHapticEvent.REWARD,
            PuppyHapticEvent.TICKET_DROP,
            PuppyHapticEvent.DANGER_CONFIRM,
            PuppyHapticEvent.TEST ->
                PuppyHapticPlan(PuppyHapticRoute.DIRECT, PuppyHapticStrength.STRONG)
        }
    }
}
