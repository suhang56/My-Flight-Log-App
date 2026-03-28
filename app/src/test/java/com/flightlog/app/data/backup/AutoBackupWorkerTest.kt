package com.flightlog.app.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutoBackupWorkerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `enqueueIfSignedIn does not crash when no account`() {
        // Should be a no-op when no Google account is signed in
        AutoBackupWorker.enqueueIfSignedIn(context)
        // No exception = pass
    }

    @Test
    fun `enqueueIfSignedIn is idempotent`() {
        // Calling multiple times should not throw
        AutoBackupWorker.enqueueIfSignedIn(context)
        AutoBackupWorker.enqueueIfSignedIn(context)
        AutoBackupWorker.enqueueIfSignedIn(context)
    }
}
