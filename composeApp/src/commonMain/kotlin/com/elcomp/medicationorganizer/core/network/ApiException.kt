package com.elcomp.medicationorganizer.core.network

enum class ApiError(val message: String, val retryable: Boolean = false) {
    INVALID_REQUEST("Sprawdź wprowadzone dane."),
    UNAUTHORIZED("Nie udało się uwierzytelnić."),
    FORBIDDEN("Brak uprawnień do wykonania operacji."),
    NOT_FOUND("Nie znaleziono danych."),
    CONFLICT("Nie można zapisać tej zmiany w obecnym stanie."),
    NETWORK("Nie udało się połączyć z serwerem.", retryable = true),
    TIMEOUT("Serwer nie odpowiedział na czas.", retryable = true),
    SERVER("Serwer chwilowo nie może obsłużyć żądania.", retryable = true),
    INVALID_RESPONSE("Serwer zwrócił nieprawidłowe dane."),
    UNEXPECTED_STATUS("Żądanie nie powiodło się."),
}

class ApiException internal constructor(
    val error: ApiError,
    val statusCode: Int? = null,
) : Exception(error.message)
