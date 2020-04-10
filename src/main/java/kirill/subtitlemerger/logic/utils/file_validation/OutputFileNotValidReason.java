package kirill.subtitlemerger.logic.utils.file_validation;

public enum OutputFileNotValidReason {
    PATH_IS_EMPTY,
    PATH_IS_TOO_LONG,
    INVALID_PATH,
    IS_A_DIRECTORY,
    DOES_NOT_EXIST,
    EXTENSION_IS_NOT_ALLOWED
}