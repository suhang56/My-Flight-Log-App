package com.flightlog.app.data.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class BackupMetadata(
    val lastBackupAt: Long,
    val flightCount: Int,
    val fileSizeBytes: Long
)

@Singleton
class BackupMetadataStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("backup_metadata", Context.MODE_PRIVATE)

    fun get(): BackupMetadata? {
        val lastAt = prefs.getLong(KEY_LAST_AT, -1L)
        if (lastAt == -1L) return null
        return BackupMetadata(
            lastBackupAt = lastAt,
            flightCount = prefs.getInt(KEY_FLIGHT_COUNT, 0),
            fileSizeBytes = prefs.getLong(KEY_FILE_SIZE, 0L)
        )
    }

    fun save(metadata: BackupMetadata) {
        prefs.edit()
            .putLong(KEY_LAST_AT, metadata.lastBackupAt)
            .putInt(KEY_FLIGHT_COUNT, metadata.flightCount)
            .putLong(KEY_FILE_SIZE, metadata.fileSizeBytes)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_LAST_AT = "backup_last_at"
        private const val KEY_FLIGHT_COUNT = "backup_flight_count"
        private const val KEY_FILE_SIZE = "backup_file_size"
    }
}
