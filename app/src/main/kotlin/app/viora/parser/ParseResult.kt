package app.viora.parser

sealed interface ParseResult<out T> {
    data class Success<T>(val value: T) : ParseResult<T>
    data object AuthenticationRequired : ParseResult<Nothing>
    data class InvalidDocument(val reason: String) : ParseResult<Nothing>
}
