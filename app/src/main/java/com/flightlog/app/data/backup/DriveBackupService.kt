package com.flightlog.app.data.backup

import android.content.Context
import android.util.Log
import com.flightlog.app.BuildConfig
import com.flightlog.app.data.auth.AuthRepository
import com.flightlog.app.data.export.ExportService
import com.flightlog.app.data.export.LogbookFlightExportWrapper
import com.flightlog.app.data.local.entity.LogbookFlight
import com.flightlog.app.data.repository.LogbookRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
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
import java.io.IOException
import kotlin.math.roundToInt
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
                ?: return@withContext BackupResult.Failure(
                    "Please sign in again with Google to grant Drive access"
                )

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
        } catch (e: UserRecoverableAuthIOException) {
            Log.e(TAG, "Backup failed: Drive scope not granted")
            BackupResult.Failure("Please re-authorise Google Drive access")
        } catch (e: IOException) {
            Log.e(TAG, "Backup failed: network/auth error", e)
            BackupResult.Failure("Network error — check connection and try signing in again")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Backup failed", e)
            else Log.e(TAG, "Backup failed")
            BackupResult.Failure(e.message ?: "Backup failed (${e.javaClass.simpleName})")
        }
    }

    suspend fun restore(): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val driveService = buildDriveService()
                ?: return@withContext RestoreResult.Failure(
                    "Please sign in again with Google to grant Drive access"
                )

            val fileId = findBackupFileId(driveService)
                ?: return@withContext RestoreResult.Failure("No backup found on Drive")

            val outputStream = ByteArrayOutputStream()
            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            val jsonString = outputStream.toString(Charsets.UTF_8.name())

            val adapter = moshi.adapter(LogbookFlightExportWrapper::class.java)
            val wrapper = adapter.fromJson(jsonString)
                ?: return@withContext RestoreResult.Failure("Failed to parse backup file")

            val now = System.currentTimeMillis()
            val flights = wrapper.flights.map { exportFlight ->
                val depTimeMillis = exportFlight.departureTimeUtc
                val depZone = exportFlight.departureTimezone?.let {
                    runCatching { java.time.ZoneId.of(it) }.getOrNull()
                } ?: java.time.ZoneId.systemDefault()
                val depEpochDay = java.time.Instant.ofEpochMilli(depTimeMillis)
                    .atZone(depZone).toLocalDate().toEpochDay()

                // Prefer new distance_km field; fall back to legacy distance_nm for old backups
                val distance = exportFlight.distanceKm
                    ?: exportFlight.distanceNmLegacy?.let { nm -> (nm * 1.852).roundToInt() }

                LogbookFlight(
                    id = 0,
                    flightNumber = exportFlight.flightNumber ?: "",
                    departureCode = exportFlight.departure,
                    arrivalCode = exportFlight.arrival,
                    departureDateEpochDay = depEpochDay,
                    departureTimeMillis = depTimeMillis,
                    arrivalTimeMillis = exportFlight.arrivalTimeUtc,
                    departureTimezone = exportFlight.departureTimezone,
                    arrivalTimezone = exportFlight.arrivalTimezone,
                    distanceKm = distance,
                    aircraftType = exportFlight.aircraftType,
                    registration = exportFlight.registration,
                    seatClass = exportFlight.seatClass,
                    seatNumber = exportFlight.seatNumber,
                    notes = exportFlight.notes,
                    rating = exportFlight.rating,
                    createdAt = exportFlight.createdAt ?: now,
                    updatedAt = exportFlight.updatedAt ?: now
                )
            }

            val results = repository.insertAllForRestore(flights)
            val imported = results.count { it != -1L }
            val skipped = results.size - imported

            RestoreResult.Success(imported = imported, skipped = skipped)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: UserRecoverableAuthIOException) {
            Log.e(TAG, "Restore failed: Drive scope not granted")
            RestoreResult.Failure("Please re-authorise Google Drive access")
        } catch (e: IOException) {
            Log.e(TAG, "Restore failed: network/auth error", e)
            RestoreResult.Failure("Network error — check connection and try signing in again")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Restore failed", e)
            else Log.e(TAG, "Restore failed")
            RestoreResult.Failure(e.message ?: "Restore failed (${e.javaClass.simpleName})")
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

    @Suppress("DEPRECATION")
    private fun buildDriveService(): Drive? {
        val user = authRepository.currentUser.value ?: return null
        if (!user.isGoogleProvider) return null

        // Use the GoogleSignIn account which holds the actual Account object
        // registered with AccountManager -- required by GoogleAccountCredential.
        val googleAccount = GoogleSignIn.getLastSignedInAccount(context) ?: return null

        // Verify Drive appdata scope is granted before building the client.
        // If the user signed in before the scope was added, the cached session
        // won't have DRIVE_APPDATA. Sign out the stale session so the next
        // login re-requests the scope via GoogleSignInOptions.
        val driveScope = Scope(DriveScopes.DRIVE_APPDATA)
        if (!GoogleSignIn.hasPermissions(googleAccount, driveScope)) {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
            GoogleSignIn.getClient(context, gso).signOut()
            Log.w(TAG, "Drive scope not granted on cached account — signed out stale session")
            return null
        }

        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = googleAccount.account ?: return null

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
