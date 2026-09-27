package com.harleytg.puppyclicker

internal enum class PuppyErrorSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

internal enum class PuppyErrorDomain {
    APP,
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

/** General Puppy Clicker failures. */
internal enum class AppErrorCode(
    override val code: String,
    override val title: String,
    override val defaultMessage: String,
    override val recovery: String,
    override val severity: PuppyErrorSeverity = PuppyErrorSeverity.ERROR
) : PuppyErrorCode {
    PASSWORD_REQUIRED(
        "APP-SAVE-101",
        "Backup password required",
        "Enter the backup password used when this save was exported.",
        "Enter the exact backup password and try again.",
        PuppyErrorSeverity.WARNING
    ),
    PASSWORD_TOO_SHORT(
        "APP-SAVE-102",
        "Backup password too short",
        "Backup passwords must contain at least 8 characters.",
        "Use a password with at least 8 characters."
    ),
    FILE_OPEN_FAILED(
        "APP-FILE-201",
        "File could not be opened",
        "Puppy Clicker could not read the selected file.",
        "Choose the file again. If it is in cloud storage, download it locally first."
    ),
    FILE_TOO_LARGE(
        "APP-FILE-202",
        "File is too large",
        "The selected save is larger than the supported 4 MB limit.",
        "Choose an original Puppy Clicker .pupsave file smaller than 4 MB."
    ),
    INVALID_SAVE_FILE(
        "APP-SAVE-203",
        "Invalid save file",
        "The selected file is not a recognized Puppy Clicker save.",
        "Choose a valid .pupsave exported by Puppy Clicker."
    ),
    DECRYPT_FAILED(
        "APP-SAVE-204",
        "Backup could not be decrypted",
        "The backup password is incorrect, or the encrypted save file is damaged.",
        "Re-enter the exact export password. If it still fails, try another untouched backup."
    ),
    INVALID_SAVE_PAYLOAD(
        "APP-SAVE-205",
        "Save data is incomplete",
        "The decrypted backup is missing required Puppy Clicker data.",
        "Use an untouched backup exported by the current Puppy Clicker save system."
    ),
    UNSUPPORTED_SAVE_VERSION(
        "APP-SAVE-206",
        "Save version is not supported",
        "This Puppy Clicker build cannot import the selected save version.",
        "Update Puppy Clicker or use Support migration for this backup."
    ),
    ACTIVE_CASINO_ROUND(
        "APP-CASINO-501",
        "Casino round is still active",
        "Puppy Clicker cannot replace the save while a Casino round is in progress.",
        "Finish or recover the active Casino round, then try again.",
        PuppyErrorSeverity.WARNING
    ),
    CASINO_STATE_INVALID(
        "APP-CASINO-502",
        "Casino state is inconsistent",
        "Casino transaction or reward data failed consistency validation.",
        "Recover the Casino state or export a fresh known-good backup before retrying."
    ),
    FILE_WRITE_FAILED(
        "APP-FILE-603",
        "File could not be written",
        "Puppy Clicker could not write the requested file.",
        "Choose another writable location and try again."
    ),
    NETWORK_UNAVAILABLE(
        "APP-NET-701",
        "Network unavailable",
        "Puppy Clicker could not reach the required service.",
        "Check your connection and try again."
    ),
    REQUEST_FAILED(
        "APP-NET-702",
        "Request failed",
        "The requested online operation did not complete.",
        "Try again. If it keeps failing, include this error code when contacting Support."
    ),
    AUTH_FAILED(
        "APP-AUTH-801",
        "Authentication failed",
        "Puppy Clicker could not verify the requested account session.",
        "Reconnect the account and try again."
    ),
    INVALID_APP_STATE(
        "APP-STATE-901",
        "App state is not ready",
        "Puppy Clicker cannot complete this action from the current state.",
        "Return to the previous screen and try the action again."
    ),
    IMPORT_UNKNOWN(
        "APP-SAVE-998",
        "Import failed",
        "Puppy Clicker encountered an unexpected error while importing this backup.",
        "No save data was imported. Try another known-good backup or contact Support."
    ),
    UNEXPECTED(
        "APP-999",
        "Unexpected Puppy Clicker error",
        "Puppy Clicker encountered an unexpected error.",
        "Try the action again. If it repeats, include this error code when contacting Support."
    );

    override val domain: PuppyErrorDomain = PuppyErrorDomain.APP
}

/** PupEye security / integrity failures. */
internal enum class PupEyeErrorCode(
    override val code: String,
    override val title: String,
    override val defaultMessage: String,
    override val recovery: String,
    override val severity: PuppyErrorSeverity = PuppyErrorSeverity.ERROR
) : PuppyErrorCode {
    AUTH_PROOF_MISSING(
        "PUPEYE-AUTH-201",
        "Authenticated ownership proof is missing",
        "This backup predates the current PupEye authenticated save format.",
        "Use Puppy Clicker Support migration so the backup can be converted safely.",
        PuppyErrorSeverity.WARNING
    ),
    AUTH_PROOF_INVALID(
        "PUPEYE-AUTH-202",
        "Authenticated ownership proof is invalid",
        "PupEye could not validate the backup's ownership proof.",
        "Re-export from the original installation or use Support migration.",
        PuppyErrorSeverity.CRITICAL
    ),
    SIGNATURE_INVALID(
        "PUPEYE-SAVE-301",
        "Authenticated signature mismatch",
        "The backup decrypted successfully, but its signed game data no longer matches the original export.",
        "Use an untouched backup from this installation. Developer identity does not bypass save authentication.",
        PuppyErrorSeverity.CRITICAL
    ),
    ROLLBACK_BLOCKED(
        "PUPEYE-SAVE-303",
        "Older save rollback blocked",
        "This authentic backup is older than the protected save history on this installation.",
        "Use Support recovery only if you intentionally need to restore an older backup.",
        PuppyErrorSeverity.WARNING
    ),
    DEVICE_TRANSFER_REQUIRED(
        "PUPEYE-ID-401",
        "Device transfer required",
        "This authentic save belongs to a different registered Puppy Clicker installation.",
        "Use the Support-authorized device-transfer flow and provide the PupEye Support Installation Code.",
        PuppyErrorSeverity.WARNING
    ),
    IDENTITY_MISMATCH(
        "PUPEYE-ID-402",
        "Player identity does not match",
        "The backup belongs to a different Puppy Clicker Player ID, Friend Code, or username.",
        "Use the correct account backup or a Support-authorized identity migration."
    ),
    CURRENT_SAVE_INVALID(
        "PUPEYE-INTEGRITY-601",
        "Current protected save failed verification",
        "PupEye could not authenticate the current protected save.",
        "Do not overwrite it. Use the recovery flow or contact Support.",
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
        "Stop protected progression and use Support recovery before changing the save.",
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
        "Do not modify protected data. Retry once or contact Support with this error code.",
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
    fun app(
        code: AppErrorCode,
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

    fun fromThrowable(
        error: Throwable,
        fallback: PuppyErrorCode
    ): PuppyError =
        (error as? PuppyAppException)?.puppyError
            ?: PuppyError(
                definition = fallback,
                developerDetail = error.message
            )
}

internal data class SaveTransferResult(
    val success: Boolean,
    val message: String,
    val error: PuppyError? = null
) {
    val supportCode: String?
        get() = error?.code
}
