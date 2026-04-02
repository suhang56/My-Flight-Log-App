package com.flightlog.app.data.backup

import android.content.Context
import android.util.Log
import com.flightlog.app.BuildConfig
import com.flightlog.app.data.auth.AuthRepository
import com.flightlog.app.data.export.ExportService
import com.flightlog.app.data.export.LogbookFlightExportWrapper
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.repository.LogbookRepository
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed class BackupResult {
    data class Success(val flightCount: Int, val fileSizeBytes: Long) : BackupResult()
    data class Failure(val reason: String) : BackupResult()
}

sealed class RestoreResult {
    data class Success(val imported: Int, val skipped: Int) : RestoreResult()
    data class Failure(val reason: String) : RestoreResult()
}

@Singleton
class DriveBackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exportService: ExportService,
    private val repository: LogbookRepository,
    private val moshi: Moshi,
    private val metadataStore: BackupMetadataStore,
    private val authRepository: AuthRepository
) {

    suspend fun backup(): BackupResult = withContext(Dispatchers.IO) {
        try {
            val driveService = buildDriveService()
                ?: return@withContext BackupResult.Failure("Drive backup requires Google sign-in")

            val flights = repository.getAllOnce()
            val jsonFile = exportService.exportToJson(flights)
            val fileSize = jsonFile.length()

            val existingFileId = findBackupFileId(driveService)

            if (existingFileId != null) {
                val content = FileContent("application/json", jsonFile)
                driveService.files().update(existingFileId, null, content).execute()
            } else {
                val fileMetadata = DriveFile().apply {
                    name = BACKUP_FILE_NAME
                    parents = listOf("appDataFolder")
                }
                val content = FileContent("application/json", jsonFile)
                driveService.files().create(fileMetadata, content)
                    .setFields("id")
                    .execute()
            }

            val metadata = BackupMetadata(
                lastBackupAt = System.currentTimeMillis(),
                flightCount = flights.size,
                fileSizeBytes = fileSize
            )
            metadataStore.save(metadata)

            jsonFile.delete()

            BackupResult.Success(flightCount = flights.size, fileSizeBytes = fileSize)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Backup failed", e)
            else Log.e(TAG, "Backup failed")
            BackupResult.Failure(e.message ?: "Unknown error")
        }
    }

    suspend fun restore(): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val driveService = buildDriveService()
                ?: return@withContext RestoreResult.Failure("Drive backup requires Google sign-in")

            val fileId = findBackupFileId(driveService)
                ?: return@withContext RestoreResult.Failure("No backup found on Drive")

            val outputStream = ByteArrayOutputStream()
            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            val jsonString = outputStream.toString(Charsets.UTF_8.name())

            val adapter = moshi.adapter(LogbookFlightExportWrapper::class.java)
            val wrapper = adapter.fromJson(jsonString)
                ?: return@withContext RestoreResult.Failure("Failed to parse backup file")

            val flights = wrapper.flights.map { exportFlight ->
                LogbookFlight(
                    id = 0,
                    flightNumber = exportFlight.flightNumber ?: "",
                    departureCode = exportFlight.departure,
                    arrivalCode = exportFlight.arrival,
                    departureTimeUtc = exportFlight.departureTimeUtc,
                    arrivalTimeUtc = exportFlight.arrivalTimeUtc,
                    departureTimezone = exportFlight.departureTimezone,
                    arrivalTimezone = exportFlight.arrivalTimezone,
                    distanceNm = exportFlight.distanceNm,
                    aircraftType = exportFlight.aircraftType ?: "",
                    seatClass = exportFlight.seatClass ?: "",
                    seatNumber = exportFlight.seatNumber ?: "",
                    notes = exportFlight.notes ?: ""
                )
            }

            val results = repository.insertAllForRestore(flights)
            val imported = results.count { it != -1L }
            val skipped = results.size - imported

            try { repository.checkAchievements() } catch (_: Exception) { }

            RestoreResult.Success(imported = imported, skipped = skipped)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Restore failed", e)
            else Log.e(TAG, "Restore failed")
            RestoreResult.Failure(e.message ?: "Unknown error")
        }
    }

    suspend fun getRemoteMetadata(): BackupMetadata? = withContext(Dispatchers.IO) {
        try {
            val driveService = buildDriveService() ?: return@withContext null
            val fileId = findBackupFileId(driveService) ?: return@withContext null

            val file = driveService.files().get(fileId)
                .setFields("size,modifiedTime")
                .execute()

            val outputStream = ByteArrayOutputStream()
            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            val jsonString = outputStream.toString(Charsets.UTF_8.name())

            val adapter = moshi.adapter(LogbookFlightExportWrapper::class.java)
            val wrapper = adapter.fromJson(jsonString)

            BackupMetadata(
                lastBackupAt = file.modifiedTime?.value ?: 0L,
                flightCount = wrapper?.flightCount ?: 0,
                fileSizeBytes = file.getSize()?.toLong() ?: 0L
            )
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to get remote metadata", e)
            else Log.e(TAG, "Failed to get remote metadata")
            null
        }
    }

    private fun buildDriveService(): Drive? {
        val user = authRepository.currentUser.value ?: return null
        if (!user.isGoogleProvider) return null
        val email = user.email ?: return null

        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccountName = email

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("FlightLog")
            .build()
    }

    private fun findBackupFileId(driveService: Drive): String? {
        val result = driveService.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = '$BACKUP_FILE_NAME'")
            .setFields("files(id)")
            .setPageSize(1)
            .execute()

        return result.files?.firstOrNull()?.id
    }

    companion object {
        private const val TAG = "DriveBackupService"
        private const val BACKUP_FILE_NAME = "flight-log-backup.json"
    }
}
