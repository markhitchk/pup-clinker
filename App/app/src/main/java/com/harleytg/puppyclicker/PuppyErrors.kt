package com.harleytg.puppyclicker

internal enum class PuppyErrorSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

internal enum class PuppyErrorDomain {
    PUPPY_CLICKER,
    PUPEYE
}

internal interface PuppyErrorCode {
    val code: String
    val title: String
    val defaultMessage: String
    val recovery: String
    val severity: PuppyErrorSeverity
    val domain: PuppyErrorDomain
}

internal enum class PuppyClickerErrorCode(
    override val code: String,
    override val title: String,
    override val defaultMessage: String,
    override val recovery: String,
    override val severity: PuppyErrorSeverity = PuppyErrorSeverity.ERROR
) : PuppyErrorCode {
    CASINO_STATE_INVALID(
        "PUPPY-CASINO-502",
        "Casino state is inconsistent",
        "Casino transaction or reward data failed consistency validation.",
        "Reconnect and restore the authoritative cloud state before retrying."
    ),
    NETWORK_UNAVAILABLE(
        "PUPPY-NET-701",
        "Network unavailable",
        "Puppy Clicker could not reach the required service.",
        "Check your connection and try again."
    ),
    REQUEST_FAILED(
        "PUPPY-NET-702",
        "Request failed",
        "The requested online operation did not complete.",
        "Try again. If it keeps failing, include this error code when contacting Support."
    ),
    AUTH_FAILED(
        "PUPPY-AUTH-801",
        "Authentication failed",
        "Puppy Clicker could not verify the requested account session.",
        "Reconnect the Pup Account and try again."
    ),
    INVALID_APP_STATE(
        "PUPPY-STATE-901",
        "App state is not ready",
        "Puppy Clicker cannot complete this action from the current state.",
        "Return to the previous screen and try the action again."
    ),
    UNEXPECTED(
        "PUPPY-999",
        "Unexpected Puppy Clicker error",
        "Puppy Clicker encountered an unexpected error.",
        "Try the action again. If it repeats, include this error code when contacting Support."
    );

    override val domain: PuppyErrorDomain = PuppyErrorDomain.PUPPY_CLICKER
}

internal enum class PupEyeErrorCode(
    override val code: String,
    override val title: String,
    override val defaultMessage: String,
    override val recovery: String,
    override val severity: PuppyErrorSeverity = PuppyErrorSeverity.ERROR
) : PuppyErrorCode {
    DEVICE_TRANSFER_REQUIRED(
        "PUPEYE-ID-401",
        "Device transfer required",
        "This Pup Account is registered to another active Puppy Clicker installation.",
        "Use the supported account-recovery or Support-authorized device-transfer process.",
        PuppyErrorSeverity.WARNING
    ),
    CURRENT_SAVE_INVALID(
        "PUPEYE-INTEGRITY-601",
        "Local cache failed verification",
        "PupEye could not authenticate the current local working cache.",
        "Reconnect so Puppy Clicker can restore the authoritative Supabase state.",
        PuppyErrorSeverity.CRITICAL
    ),
    PROGRESSION_LOCKED(
        "PUPEYE-INTEGRITY-602",
        "Protected progression is locked",
        "PupEye has locked protected progression because an integrity flag requires review.",
        "Use the PupEye support information shown in Settings when contacting Support.",
        PuppyErrorSeverity.CRITICAL
    ),
    ECONOMY_LEDGER_INVALID(
        "PUPEYE-INTEGRITY-603",
        "Economy ledger verification failed",
        "PupEye detected an invalid protected economy ledger.",
        "Stop protected progression and use Support recovery before changing account state.",
        PuppyErrorSeverity.CRITICAL
    ),
    DUPLICATE_TRANSACTION(
        "PUPEYE-INTEGRITY-604",
        "Duplicate protected transaction",
        "PupEye detected a duplicate economy transaction identifier.",
        "Do not retry the transaction repeatedly. Contact Support if the state does not recover.",
        PuppyErrorSeverity.CRITICAL
    ),
    SERVER_REJECTED(
        "PUPEYE-SERVER-701",
        "PupEye server rejected the request",
        "The PupEye service rejected the protected operation.",
        "Retry once. If it repeats, include this error code and the Support Installation Code.",
        PuppyErrorSeverity.WARNING
    ),
    UNKNOWN(
        "PUPEYE-999",
        "Unexpected PupEye error",
        "PupEye encountered an unexpected security or integrity error.",
        "Retry once or contact Support with this error code.",
        PuppyErrorSeverity.CRITICAL
    );

    override val domain: PuppyErrorDomain = PuppyErrorDomain.PUPEYE
}

internal data class PuppyError(
    val definition: PuppyErrorCode,
    val message: String = definition.defaultMessage,
    val developerDetail: String? = null
) {
    val code: String get() = definition.code
    val title: String get() = definition.title
    val recovery: String get() = definition.recovery
    val severity: PuppyErrorSeverity get() = definition.severity
    val domain: PuppyErrorDomain get() = definition.domain
}

internal class PuppyAppException(
    val puppyError: PuppyError,
    cause: Throwable? = null
) : IllegalArgumentException(puppyError.message, cause)

internal object PuppyErrorHandler {
    fun puppyClicker(
        code: PuppyClickerErrorCode,
        message: String = code.defaultMessage,
        developerDetail: String? = null,
        cause: Throwable? = null
    ): PuppyAppException = PuppyAppException(
        PuppyError(code, message, developerDetail),
        cause
    )

    fun pupEye(
        code: PupEyeErrorCode,
        message: String = code.defaultMessage,
        developerDetail: String? = null,
        cause: Throwable? = null
    ): PuppyAppException = PuppyAppException(
        PuppyError(code, message, developerDetail),
        cause
    )

    fun fromThrowable(error: Throwable, fallback: PuppyErrorCode): PuppyError =
        (error as? PuppyAppException)?.puppyError
            ?: PuppyError(definition = fallback, developerDetail = error.message)
}
