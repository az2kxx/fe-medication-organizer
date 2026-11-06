package com.elcomp.medicationorganizer.core.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.DataInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val KeyStoreProvider = "AndroidKeyStore"
private const val SessionKeyAlias = "elcomp.live-session.v1"
private const val CipherTransformation = "AES/GCM/NoPadding"
private const val RecordVersion: Byte = 1
private const val NonceSize = 12
private const val TagSize = 16
private const val HeaderSize = 1 + NonceSize
private const val MaxPayloadSize = 64 * 1024
private const val MaxRecordSize = HeaderSize + TagSize + MaxPayloadSize
private val storageMutex = Mutex()

class AndroidSessionTokenStore(context: Context) : SessionTokenStore {
    private val applicationContext = context.applicationContext
    private val file by lazy {
        AtomicFile(File(applicationContext.noBackupFilesDir, "live-session.v1"))
    }

    override suspend fun read(): ByteArray? = storageOperation {
        val record = readRecord() ?: return@storageOperation null
        if (record.size !in (HeaderSize + TagSize + 1)..MaxRecordSize || record[0] != RecordVersion) {
            return@storageOperation discardRecord()
        }

        val keyStore = openKeyStore()
        val key = keyStore.getKey(SessionKeyAlias, null) as? SecretKey
            ?: return@storageOperation discardRecord()
        try {
            val cipher = Cipher.getInstance(CipherTransformation)
            val nonce = GCMParameterSpec(TagSize * Byte.SIZE_BITS, record, 1, NonceSize)
            cipher.init(Cipher.DECRYPT_MODE, key, nonce)
            cipher.doFinal(record, HeaderSize, record.size - HeaderSize)
        } catch (_: AEADBadTagException) {
            discardRecord()
        } catch (_: KeyPermanentlyInvalidatedException) {
            keyStore.deleteEntry(SessionKeyAlias)
            discardRecord()
        }
    }

    override suspend fun write(bytes: ByteArray) = storageOperation {
        if (bytes.size !in 1..MaxPayloadSize) throw SessionStorageException()

        val keyStore = openKeyStore()
        val key = keyStore.getKey(SessionKeyAlias, null) as? SecretKey ?: generateKey()
        val cipher = Cipher.getInstance(CipherTransformation)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key)
        } catch (_: KeyPermanentlyInvalidatedException) {
            keyStore.deleteEntry(SessionKeyAlias)
            cipher.init(Cipher.ENCRYPT_MODE, generateKey())
        }
        val nonce = cipher.iv
        if (nonce.size != NonceSize) throw SessionStorageException()
        val record = byteArrayOf(RecordVersion) + nonce + cipher.doFinal(bytes)

        val stream = file.startWrite()
        try {
            stream.write(record)
        } catch (failure: IOException) {
            file.failWrite(stream)
            throw failure
        }
        file.finishWrite(stream)
        // AtomicFile can log rename failures without propagating an exception.
        if (readRecord()?.contentEquals(record) != true) throw SessionStorageException()
    }

    override suspend fun clear() = storageOperation { clearRecord() }

    private fun readRecord(): ByteArray? = try {
        file.openRead().use { input ->
            val length = input.channel.size()
            if (length > MaxRecordSize) {
                ByteArray(0)
            } else {
                ByteArray(length.toInt()).also { DataInputStream(input).readFully(it) }
            }
        }
    } catch (failure: FileNotFoundException) {
        if (file.baseFile.exists()) throw failure
        null
    }

    private fun discardRecord(): ByteArray? {
        clearRecord()
        return null
    }

    private fun clearRecord() {
        file.delete()
        if (readRecord() != null) throw SessionStorageException()
    }

    private fun openKeyStore(): KeyStore = KeyStore.getInstance(KeyStoreProvider).apply {
        load(null)
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KeyStoreProvider)
        generator.init(
            KeyGenParameterSpec.Builder(
                SessionKeyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private suspend fun <T> storageOperation(block: () -> T): T = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            try {
                block()
            } catch (_: IOException) {
                throw SessionStorageException()
            } catch (_: GeneralSecurityException) {
                throw SessionStorageException()
            } catch (_: ProviderException) {
                throw SessionStorageException()
            } catch (_: SecurityException) {
                throw SessionStorageException()
            }
        }
    }
}
