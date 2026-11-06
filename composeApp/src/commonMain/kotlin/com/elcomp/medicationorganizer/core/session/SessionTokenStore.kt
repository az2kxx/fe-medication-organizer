package com.elcomp.medicationorganizer.core.session

/** Platform storage for one encrypted or Keychain-backed LIVE session record. */
interface SessionTokenStore {
    suspend fun read(): ByteArray?
    suspend fun write(bytes: ByteArray)
    suspend fun clear()
}

class SessionStorageException : Exception("Secure session storage is unavailable.")
