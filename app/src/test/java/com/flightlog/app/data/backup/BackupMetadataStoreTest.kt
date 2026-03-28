package com.flightlog.app.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupMetadataStoreTest {

    private lateinit var store: BackupMetadataStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = BackupMetadataStore(context)
        store.clear()
    }

    @Test
    fun `get returns null when no metadata saved`() {
        assertNull(store.get())
    }

    @Test
    fun `save and get round-trips correctly`() {
        val metadata = BackupMetadata(
            lastBackupAt = 1711555200000L,
            flightCount = 42,
            fileSizeBytes = 12345L
        )
        store.save(metadata)

        val result = store.get()
        assertNotNull(result)
        assertEquals(1711555200000L, result!!.lastBackupAt)
        assertEquals(42, result.flightCount)
        assertEquals(12345L, result.fileSizeBytes)
    }

    @Test
    fun `save overwrites previous metadata`() {
        store.save(BackupMetadata(1000L, 10, 500L))
        store.save(BackupMetadata(2000L, 20, 1000L))

        val result = store.get()!!
        assertEquals(2000L, result.lastBackupAt)
        assertEquals(20, result.flightCount)
        assertEquals(1000L, result.fileSizeBytes)
    }

    @Test
    fun `clear removes all metadata`() {
        store.save(BackupMetadata(1000L, 10, 500L))
        store.clear()
        assertNull(store.get())
    }

    @Test
    fun `zero flight count is valid`() {
        val metadata = BackupMetadata(
            lastBackupAt = 1000L,
            flightCount = 0,
            fileSizeBytes = 42L
        )
        store.save(metadata)
        assertEquals(0, store.get()!!.flightCount)
    }

    @Test
    fun `zero file size is valid`() {
        val metadata = BackupMetadata(
            lastBackupAt = 1000L,
            flightCount = 5,
            fileSizeBytes = 0L
        )
        store.save(metadata)
        assertEquals(0L, store.get()!!.fileSizeBytes)
    }

    @Test
    fun `large values are handled correctly`() {
        val metadata = BackupMetadata(
            lastBackupAt = Long.MAX_VALUE,
            flightCount = Int.MAX_VALUE,
            fileSizeBytes = Long.MAX_VALUE
        )
        store.save(metadata)
        val result = store.get()!!
        assertEquals(Long.MAX_VALUE, result.lastBackupAt)
        assertEquals(Int.MAX_VALUE, result.flightCount)
        assertEquals(Long.MAX_VALUE, result.fileSizeBytes)
    }

    @Test
    fun `negative lastBackupAt is never returned by get`() {
        // The store uses -1L as sentinel for "not set", so saving -1L
        // should behave as if nothing was saved
        store.save(BackupMetadata(-1L, 5, 100L))
        assertNull(store.get())
    }
}
