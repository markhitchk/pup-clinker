package com.harleytg.puppyclicker

internal enum class SaveTransferSeverity {
    WARNING,
    ERROR,
    CRITICAL
}

/**
 * Stable, user-visible save-transfer error codes.
 *
 * Keep the code strings stable: players can quote them to Support and screenshots/logs
 * can be matched to the exact failure path without exposing private save data.
 */
internal enum class SaveTransferErrorCode(
    val code: String,
    val title: String,
    val defaultMessage: String,
    val recovery: String,
    val severity: SaveTransferSeverity = SaveTransferSeverity.ERROR
) {
    PASSWORD_REQUIRED(
        "PUP-SAVE-101",
        "Backup password required",
        "Enter the backup password used when this save was exported.",
        "Enter the exact backup password and try again.",
        SaveTransferSeverity.WARNING
    ),
    PASSWORD_TOO_SHORT(
        "PUP-SAVE-102",
        "Backup password too short",
        "Backup passwords must contain at least 8 characters.",
        "Use a password with at least 8 characters."
    ),
    FILE_OPEN_FAILED(
        "PUP-SAVE-201",
        "Save file could not be opened",
        "Puppy Clicker could not read the selected save file.",
        "Choose the file again. If it is in cloud storage, download it locally first."
    ),
    FILE_TOO_LARGE(
        "PUP-SAVE-202",
        "Save file is too large",
        "The selected save is larger than the supported 4 MB limit.",
        "Choose an original Puppy Clicker .pupsave file smaller than 4 MB."
    ),
    INVALID_FILE(
        "PUP-SAVE-203",
        "Invalid save file",
        "The selected file is not a recognized Puppy Clicker save.",
        "Choose a valid .pupsave exported by Puppy Clicker."
    ),
    DECRYPT_FAILED(
        "PUP-SAVE-204",
        "Backup could not be decrypted",
        "The backup password is incorrect, or the encrypted save file is damaged.",
        "Re-enter the exact export password. If it still fails, try another untouched backup."
    ),
    INVALID_PAYLOAD(
        "PUP-SAVE-205",
        "Save data is incomplete",
        "The decrypted backup is missing required Puppy Clicker save data.",
        "Use an untouched backup exported by the current Puppy Clicker save system."
    ),
    UNSUPPORTED_VERSION(
        "PUP-SAVE-206",
        "Save version is not supported",
        "This Puppy Clicker build cannot import the selected save version.",
        "Update Puppy Clicker or use Support migration for this backup."
    ),
    LEGACY_MIGRATION_REQUIRED(
        "PUP-SAVE-207",
        "Legacy save needs migration",
        "This backup predates the current authenticated save format.",
        "Use Puppy Clicker Support migration so the backup can be converted safely."
    ),
    SIGNATURE_INVALID(
        "PUP-SAVE-301",
        "Authenticated signature mismatch",
        "The backup decrypted successfully, but its signed game data no longer matches the original export.",
        "Use an untouched backup from this installation. Developer identity does not bypass save authentication.",
        SaveTransferSeverity.CRITICAL
    ),
    OWNERSHIP_PROOF_INVALID(
        "PUP-SAVE-302",
        "Ownership proof is invalid",
        "PupEye could not validate the backup's authenticated ownership proof.",
        "Re-export from the original installation or use Puppy Clicker Support migration.",
        SaveTransferSeverity.CRITICAL
    ),
    ROLLBACK_BLOCKED(
        "PUP-SAVE-303",
        "Older save rollback blocked",
        "This authentic backup is older than the protected save history on this installation.",
        "Use Support recovery only if you intentionally need to restore an older backup.",
        SaveTransferSeverity.WARNING
    ),
    DEVICE_TRANSFER_REQUIRED(
        "PUP-SAVE-401",
        "Device transfer required",
        "This authentic save belongs to a different registered Puppy Clicker installation.",
        "Use the Support-authorized device-transfer flow and provide the PupEye Support Installation Code.",
        SaveTransferSeverity.WARNING
    ),
    IDENTITY_MISMATCH(
        "PUP-SAVE-402",
        "Player identity does not match",
        "The backup belongs to a different Puppy Clicker Player ID, Friend Code, or username.",
        "Use the correct account backup or a Support-authorized identity migration."
    ),
    ACTIVE_CASINO_ROUND(
        "PUP-SAVE-501",
        "Casino round is still active",
        "Puppy Clicker cannot replace the save while a Casino round is in progress.",
        "Finish or recover the active Casino round, then try the import again.",
        SaveTransferSeverity.WARNING
    ),
    CASINO_STATE_INVALID(
        "PUP-SAVE-502",
        "Casino save state is inconsistent",
        "Casino transaction or reward data failed consistency validation.",
        "Recover the Casino state or export a fresh known-good backup before retrying."
    ),
    CURRENT_SAVE_INVALID(
        "PUP-SAVE-601",
        "Current protected save failed verification",
        "PupEye could not authenticate the current protected save.",
        "Do not overwrite it. Use the recovery flow or contact Support.",
        SaveTransferSeverity.CRITICAL
    ),
    PUPEYE_LOCKED(
        "PUP-SAVE-602",
        "Protected progression is locked",
        "PupEye has locked protected progression because an integrity flag requires review.",
        "Use the PupEye support information shown in Settings when contacting Support.",
        SaveTransferSeverity.CRITICAL
    ),
    FILE_WRITE_FAILED(
        "PUP-SAVE-603",
        "Save file could not be written",
        "Puppy Clicker could not write the exported backup.",
        "Choose another writable location and export again."
    ),
    IMPORT_UNKNOWN(
        "PUP-SAVE-699",
        "Import failed",
        "Puppy Clicker encountered an unexpected error while importing this backup.",
        "No save data was imported. Try another known-good backup or contact Support."
    ),
    EXPORT_UNKNOWN(
        "PUP-SAVE-799",
        "Export failed",
        "Puppy Clicker encountered an unexpected error while exporting this backup.",
        "Try another writable location. If the error repeats, contact Support."
    )
}

internal class SaveTransferException(
    val errorCode: SaveTransferErrorCode,
    message: String = errorCode.defaultMessage,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

internal data class SaveTransferResult(
    val success: Boolean,
    val message: String,
    val errorCode: SaveTransferErrorCode? = null
) {
    val supportCode: String?
        get() = errorCode?.code
}
