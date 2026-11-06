package com.elcomp.medicationorganizer.features.profile.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val StoreName = "elcomp_demo_preferences"
private const val RecordKey = "accessibility.v1"

class AndroidDemoPreferencesStore(context: Context) : DemoPreferencesStore {
    private val applicationContext = context.applicationContext
    private val preferences by lazy {
        applicationContext.getSharedPreferences(StoreName, Context.MODE_PRIVATE)
    }

    override suspend fun read(): String? = storageOperation {
        preferences.all[RecordKey] as? String
    }

    override suspend fun write(record: String): Unit = storageOperation {
        if (!preferences.edit().putString(RecordKey, record).commit()) {
            throw DemoPreferencesStorageException()
        }
    }

    override suspend fun clear(): Unit = storageOperation {
        if (!preferences.edit().remove(RecordKey).commit()) {
            throw DemoPreferencesStorageException()
        }
    }

    private suspend fun <T> storageOperation(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (_: SecurityException) {
            throw DemoPreferencesStorageException()
        } catch (_: IllegalStateException) {
            throw DemoPreferencesStorageException()
        }
    }
}
