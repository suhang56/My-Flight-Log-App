package com.flightlog.app.data.backup

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.signin.GoogleSignIn
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val driveBackupService: DriveBackupService
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val account = GoogleSignIn.getLastSignedInAccount(applicationContext)
        if (account == null) {
            Log.d(TAG, "No signed-in account, skipping auto-backup")
            return Result.success()
        }

        return when (val result = driveBackupService.backup(account)) {
            is BackupResult.Success -> {
                Log.d(TAG, "Auto-backup succeeded: ${result.flightCount} flights")
                Result.success()
            }
            is BackupResult.Failure -> {
                Log.e(TAG, "Auto-backup failed: ${result.reason}")
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "AutoBackupWorker"
        private const val WORK_NAME = "auto_backup"

        fun enqueueIfSignedIn(context: Context) {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                if (account == null) return

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context)
                    .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
            } catch (_: Exception) {
                // Side-effect — never block the caller
            }
        }
    }
}
