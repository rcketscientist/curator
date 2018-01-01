package com.anthonymandra.rawdroid.data

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.arch.persistence.room.Room
import android.support.test.InstrumentationRegistry
import android.support.test.filters.MediumTest
import android.support.test.runner.AndroidJUnit4

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertTrue
import org.hamcrest.CoreMatchers.*
import org.junit.Assert.assertEquals
import java.io.StringReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.hasSize
import org.junit.Rule


@RunWith(AndroidJUnit4::class)
@MediumTest
class AppDatabaseTest {

    @Rule @JvmField
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var folderDao: FolderDao
    private lateinit var metadataDao: MetadataDao
    private lateinit var subjectDao: SubjectDao
    private lateinit var subjectJunctionDao: SubjectJunctionDao

    private val folderId = 16L
    private val host = "content://com.android.externalstorage.documents"
    private val tree = "tree"
    private val document = "document"
    private val treeId = "00000-00000:images"
    private val testFolder: FolderEntity
        get() = FolderEntity(
                "source:folder/file",
                folderId,
                "/16",
                0,
                null)

    private val testSubjectsCount = 11  // Don't count synonyms
    private val testSubjects =
            "Cathedral\n" +
            "National Park\n" +
                "\tBadlands\n" +
                "\tBryce Canyon\n" +
                "\tGrand Teton\n" +
                "\tHaesindang Park\n" +
                    "\t\t{Penis Park}\n" +
            "Europe\n" +
                "\tGermany\n" +
                    "\t\tTrier\n" +
                "\tFrance\n" +
            "Roman ruins"

    @Before
    @Throws(Exception::class)
    fun setUp() {
        // using an in-memory database because the information stored here disappears when the
        // process is killed
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getContext(),
                AppDatabase::class.java)
                // allowing main thread queries, just for testing
                .allowMainThreadQueries()
                .build()

        folderDao = db.folderDao()
        metadataDao = db.metadataDao()
        subjectDao = db.subjectDao()
        subjectJunctionDao = db.subjectJunctionDao()
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
                first.documentUri,
                first.id,
                first.path,
                first.depth, null)

        updated.documentUri = "source:/folder/updated_file"
        folderDao.update(updated)
        assertFolder(updated)

        val child = FolderEntity(
                first.documentUri,
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
        val first = getTestData(1)

        val imageId = metadataDao.insert(first)

        assertMetadata(first)

        val updated = getTestData(2)
        updated.id = imageId

        metadataDao.update(updated)
        assertMetadata(updated)

        metadataDao.delete(updated)
        assertEquals(0, metadataDao.count().toLong())
    }

    @Test
    fun subjects() {
        assertThat(subjectDao.count(), equalTo(0))

        val reader = StringReader(testSubjects)
        subjectDao.importKeywords(InstrumentationRegistry.getTargetContext(), reader)
        assertThat(subjectDao.count(), equalTo(testSubjectsCount))

        val europe = subjectDao.get(7)
        assertThat(europe.name, equalTo("Europe"))

        val europeanEntities = subjectDao.getDescendants(7)
        assertThat(europeanEntities, hasSize(4))
        val europeanNames = europeanEntities.map { it.name }
        assertThat(europeanNames, hasItems("Europe", "Germany", "Trier", "France"))

        val trier = subjectDao.get(9)
        assertThat(trier.name, equalTo("Trier"))

        val trierTree = subjectDao.getAncestors(9)
        assertThat(trierTree, hasSize(3))
        val trierTreeNames = trierTree.map { it.name }
        assertThat(trierTreeNames, hasItems("Europe", "Germany", "Trier"))

        val time = System.currentTimeMillis()
        europeanEntities.forEach( {it.recent = time})

        subjectDao.update(europeanEntities)

        val updateEuropeanEntities = subjectDao.getDescendants(7)
        updateEuropeanEntities.forEach { europeanEntity ->
            assertThat(europeanEntity.recent, equalTo(time))
        }
        subjectDao.deleteAll()
        assertThat(subjectDao.count(), equalTo(0))
    }

    @Test
    fun subjectJunction() {
        // Prep parents
        val folderId = folderDao.insert(testFolder)
        assertEquals(1, folderDao.count().toLong())

        // Prep images
        val image1 = getTestData(1)
        val image2 = getTestData(2)

        val imageId1 = metadataDao.insert(image1)
        val imageId2 = metadataDao.insert(image2)

        // Prep subjects
        val reader = StringReader(testSubjects)
        subjectDao.importKeywords(InstrumentationRegistry.getTargetContext(), reader)
        assertThat(subjectDao.count(), equalTo(testSubjectsCount))

        /**
         *          / Subject 1 (Cathedral)
         *  Image 1
         *         \ Subject 2 (National Park)
         *
         *                       / Image 1
         *  Subject 1 (Cathedral)
         *                      \ Image 2
         */
        val subjectRelation1 = SubjectJunction(imageId1, 1)
        val subjectRelation2 = SubjectJunction(imageId1, 2)
        val subjectRelation3 = SubjectJunction(imageId2, 1)

        subjectJunctionDao.insert(subjectRelation1)
        subjectJunctionDao.insert(subjectRelation2)
        subjectJunctionDao.insert(subjectRelation3)

        val imagesWith1 = subjectJunctionDao.getImagesWith(1)
        val imagesWith2 = subjectJunctionDao.getImagesWith(2)

        val subjectsFor1 = subjectJunctionDao.getSubjectsFor(imageId1)
        val subjectsFor2 = subjectJunctionDao.getSubjectsFor(imageId2)

        assertThat(imagesWith1, hasItems(imageId1,imageId2))
        assertThat(imagesWith2, hasItems(imageId1))
        assertThat(subjectsFor1, hasItems(1L,2L))
        assertThat(subjectsFor2, hasItems(1L))

        val liveAll = metadataDao.all
        val all = liveAll.blockingObserve()
        val liveJoin = metadataDao.images
        val joinResult = liveJoin.blockingObserve()

        val liveJoin2 = metadataDao.images2
        val joinResult2 = liveJoin2.blockingObserve()

        // Ensure we don't have separate entities per junction match
        assertThat(joinResult!!.size, equalTo(2))

        assertThat(joinResult[0].keywords, hasItems("Cathedral", "National Park"))
        assertThat(joinResult[1].keywords, hasItems("Cathedral"))
    }

    private fun getTestData(suffix: Int): MetadataEntity {
        val meta = MetadataEntity()
        meta.altitude = "altitude" + suffix
        meta.aperture = "aperture" + suffix
        meta.driveMode = "driveMode" + suffix
        meta.exposure = "exposure" + suffix
        meta.exposureMode = "exposureMode" + suffix
        meta.exposureProgram = "exposureProgram" + suffix
        meta.flash = "flash" + suffix
        meta.focalLength = "focalLength" + suffix
        meta.height = suffix
        meta.width = suffix
        meta.iso = "iso" + suffix
        meta.latitude = "latitude" + suffix
        meta.lens = "lens" + suffix
        meta.longitude = "longitude" + suffix
        meta.make = "make" + suffix
        meta.orientation = suffix
        meta.timestamp = "timestamp" + suffix
        meta.model = "model" + suffix
        meta.whiteBalance = "whiteBalance" + suffix
        meta.label = "label" + suffix
        meta.rating = "rating" + suffix
        meta.parentId = folderId
        meta.name = "image$suffix.cr2"
        meta.documentId = "$treeId/${meta.name}"
        meta.uri = "$host/$tree/$treeId/$document/${meta.documentId}"
        return meta
    }

    private fun assertFolder(entity: FolderEntity) {
        val results = folderDao.all.blockingObserve()

        assertNotNull(results)
        assertEquals(1, results!!.size.toLong())
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