package com.anthonymandra.rawdroid.data

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.support.test.InstrumentationRegistry
import android.support.test.filters.MediumTest
import android.support.test.runner.AndroidJUnit4

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertTrue
import org.junit.Assert.assertEquals
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@MediumTest
class AppDatabaseTest {
    private lateinit var db: AppDatabase
    private lateinit var folderDao: FolderDao
    private lateinit var metadataDao: MetadataDao
    private lateinit var subjectDao: SubjectDao

    private val folderId = 16L

    private val testFolder: FolderEntity
        get() = FolderEntity(
                "source:folder/file",
                folderId,
                "/16",
                0,
                null)

    @Before
    @Throws(Exception::class)
    fun setUp() {
        db = AppDatabase.create(InstrumentationRegistry.getTargetContext(), true)
        folderDao = db.folderDao()
        metadataDao = db.metadataDao()
        subjectDao = db.subjectDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun folders() {
        assertEquals(0, folderDao.count().toLong())

        val first = testFolder

        val id = folderDao.insert(first)

        assertEquals(folderId, id)    // Can we override the autoGenerate?

        assertFolder(first)

        val updated = FolderEntity(
                first.documentId,
                first.id,
                first.path,
                first.depth, null)

        updated.documentId = "source:/folder/updated_file"
        folderDao.update(updated)
        assertFolder(updated)

        val child = FolderEntity(
                first.documentId,
                first.id + 1,
                "/16/17",
                first.depth,
                first.id)

        val childId = folderDao.insert(child)

        folderDao.delete(updated, child)
        assertEquals(0, folderDao.count().toLong())
    }

    @Test
    fun metadata() {
        val folderId = folderDao.insert(testFolder)
        assertEquals(1, folderDao.count().toLong())

        assertEquals(0, metadataDao.count().toLong())
        val first = getTestData(false)

        val imageId = metadataDao.insert(first)

        assertMetadata(first)

        val updated = getTestData(true)
        updated.id = imageId

        metadataDao.update(updated)
        assertMetadata(updated)

        metadataDao.delete(updated)
        assertEquals(0, metadataDao.count().toLong())
    }

    private fun getTestData(update: Boolean): MetadataEntity {
        val meta = MetadataEntity()
        meta.altitude = if (update) "altitude" else "altitude2"
        meta.aperture = if (update) "aperture" else "aperture2"
        meta.driveMode = if (update) "driveMode" else "driveMode2"
        meta.exposure = if (update) "exposure" else "exposure2"
        meta.exposureMode = if (update) "exposureMode" else "exposureMode2"
        meta.exposureProgram = if (update) "exposureProgram" else "exposureProgram2"
        meta.flash = if (update) "flash" else "flash2"
        meta.focalLength = if (update) "focalLength" else "focalLength2"
        meta.height = if (update) 1080 else 2160
        meta.width = if (update) 1920 else 3840
        meta.iso = if (update) "iso" else "iso2"
        meta.latitude = if (update) "latitude" else "latitude2"
        meta.lens = if (update) "lens" else "lens2"
        meta.longitude = if (update) "longitude" else "longitude2"
        meta.make = if (update) "make" else "make2"
        meta.orientation = if (update) 12 else 24
        meta.timestamp = if (update) "timestamp" else "timestamp2"  //TODO: long?
        meta.model = if (update) "model" else "model2"
        meta.whiteBalance = if (update) "whiteBalance" else "whiteBalance2"
        meta.label = if (update) "label" else "label2"
        meta.rating = if (update) "rating" else "rating2"
        meta.parent = folderId
        //		meta.subject = update ? "subject" : "subject2";
        return meta
    }

    private fun assertFolder(entity: FolderEntity) {
        val results = folderDao.all

        assertNotNull(results)
        assertEquals(1, results.size.toLong())
        assertTrue(entity == results[0])

        val result = folderDao.get(entity.id)

        assertNotNull(result)
        assertTrue(result == entity)
    }

    private fun assertMetadata(entity: MetadataEntity) {
        val results = metadataDao.all.blockingObserve()

        assertNotNull(results)
        assertEquals(1, results!!.size)
        assertMetaEquality(results[0], entity)
    }

    private fun assertMetaEquality(one: MetadataEntity, two: MetadataEntity) {
        assertTrue(one.altitude == two.altitude)
        assertTrue(one.aperture == two.aperture)
        assertTrue(one.driveMode == two.driveMode)
        assertTrue(one.exposure == two.exposure)
        assertTrue(one.exposureMode == two.exposureMode)
        assertTrue(one.exposureProgram == two.exposureProgram)
        assertTrue(one.flash == two.flash)
        assertTrue(one.focalLength == two.focalLength)
        assertTrue(one.height == two.height)
        assertTrue(one.width == two.width)
        assertTrue(one.iso == two.iso)
        assertTrue(one.latitude == two.latitude)
        assertTrue(one.lens == two.lens)
        assertTrue(one.longitude == two.longitude)
        assertTrue(one.make == two.make)
        assertTrue(one.orientation == two.orientation)
        assertTrue(one.timestamp == two.timestamp)
        assertTrue(one.model == two.model)
        assertTrue(one.whiteBalance == two.whiteBalance)
        assertTrue(one.label == two.label)
        assertTrue(one.rating == two.rating)
//        assertTrue(one.subject == two.subject)
    }

    // LiveData uses a lazy observe so we must block to test it
    fun <T> LiveData<T>.blockingObserve(): T? {
        var value: T? = null
        val latch = CountDownLatch(1)
        val innerObserver = Observer<T> {
            value = it
            latch.countDown()
        }
        observeForever(innerObserver)
        latch.await(2, TimeUnit.SECONDS)
        return value
    }
}