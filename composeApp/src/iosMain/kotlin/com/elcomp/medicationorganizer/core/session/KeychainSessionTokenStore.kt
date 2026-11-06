package com.elcomp.medicationorganizer.core.session

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataGetTypeID
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFGetTypeID
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDecode
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecAttrSynchronizable
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

private const val KeychainService = "com.elcomp.medicationorganizer.session"
private const val KeychainAccount = "live.v1"
private const val MaxPayloadSize = 64 * 1024
private val storageMutex = Mutex()

@OptIn(ExperimentalForeignApi::class)
internal class KeychainSessionTokenStore : SessionTokenStore {
    override suspend fun read(): ByteArray? = storageOperation {
        withSessionQuery { query ->
            query.put(kSecReturnData, kCFBooleanTrue)
            query.put(kSecMatchLimit, kSecMatchLimitOne)
            memScoped {
                val result = alloc<CFTypeRefVar>()
                result.value = null
                val status = SecItemCopyMatching(query, result.ptr)
                val item = result.value
                try {
                    when (status) {
                        errSecItemNotFound -> null
                        errSecDecode -> discardRecord()
                        errSecSuccess -> decodeData(item) ?: discardRecord()
                        else -> throw SessionStorageException()
                    }
                } finally {
                    if (item != null) CFRelease(item)
                }
            }
        }
    }

    override suspend fun write(bytes: ByteArray) = storageOperation {
        if (bytes.size !in 1..MaxPayloadSize) throw SessionStorageException()
        val data = bytes.usePinned {
            CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.toLong())
        } ?: throw SessionStorageException()
        try {
            withSessionQuery { query ->
                val status = withDictionary { changes ->
                    changes.put(kSecValueData, data)
                    SecItemUpdate(query, changes)
                }
                when (status) {
                    errSecSuccess -> Unit
                    errSecItemNotFound -> {
                        query.put(kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
                        query.put(kSecValueData, data)
                        if (SecItemAdd(query, null) != errSecSuccess) throw SessionStorageException()
                    }
                    else -> throw SessionStorageException()
                }
            }
        } finally {
            CFRelease(data)
        }
    }

    override suspend fun clear() = storageOperation { clearRecord() }

    private fun decodeData(item: CFTypeRef?): ByteArray? {
        if (item == null || CFGetTypeID(item) != CFDataGetTypeID()) return null
        val data: CFDataRef = item.reinterpret()
        val size = CFDataGetLength(data)
        if (size !in 1L..MaxPayloadSize.toLong()) return null
        return CFDataGetBytePtr(data)?.reinterpret<ByteVar>()?.readBytes(size.toInt())
    }

    private fun discardRecord(): ByteArray? {
        clearRecord()
        return null
    }

    private fun clearRecord() {
        withSessionQuery { query ->
            when (SecItemDelete(query)) {
                errSecSuccess, errSecItemNotFound -> Unit
                else -> throw SessionStorageException()
            }
        }
    }

    private fun <T> withSessionQuery(block: (CFMutableDictionaryRef) -> T): T =
        withDictionary { query ->
            query.put(kSecClass, kSecClassGenericPassword)
            query.putString(kSecAttrService, KeychainService)
            query.putString(kSecAttrAccount, KeychainAccount)
            query.put(kSecAttrSynchronizable, kCFBooleanFalse)
            block(query)
        }

    private fun <T> withDictionary(block: (CFMutableDictionaryRef) -> T): T {
        // CF-type callbacks retain values for the lifetime of the dictionary.
        val dictionary = CFDictionaryCreateMutable(
            null,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        ) ?: throw SessionStorageException()
        return try {
            block(dictionary)
        } finally {
            CFRelease(dictionary)
        }
    }

    private fun CFMutableDictionaryRef.put(key: CFStringRef?, value: CFTypeRef?) {
        if (key == null || value == null) throw SessionStorageException()
        CFDictionarySetValue(this, key, value)
    }

    private fun CFMutableDictionaryRef.putString(key: CFStringRef?, text: String) {
        val value = CFStringCreateWithCString(null, text, kCFStringEncodingUTF8)
            ?: throw SessionStorageException()
        try {
            put(key, value)
        } finally {
            CFRelease(value)
        }
    }

    private suspend fun <T> storageOperation(block: () -> T): T = withContext(Dispatchers.IO) {
        storageMutex.withLock { block() }
    }
}
