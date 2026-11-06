package com.elcomp.medicationorganizer.features.profile.data

import platform.Foundation.NSUserDefaults

private const val RecordKey = "com.elcomp.medicationorganizer.demo.accessibility.v1"

internal class IosDemoPreferencesStore : DemoPreferencesStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun read(): String? = defaults.stringForKey(RecordKey)

    override suspend fun write(record: String) {
        defaults.setObject(record, forKey = RecordKey)
    }

    override suspend fun clear() {
        defaults.removeObjectForKey(RecordKey)
    }
}
