package com.elcomp.medicationorganizer.features.profile.data

/** Device storage for the built-in demo account's accessibility preferences. */
interface DemoPreferencesStore {
    suspend fun read(): String?
    suspend fun write(record: String)
    suspend fun clear()
}

class DemoPreferencesStorageException : Exception("Demo preferences storage is unavailable.")
