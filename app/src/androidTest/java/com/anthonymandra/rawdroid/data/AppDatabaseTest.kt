package com.anthonymandra.rawdroid.data

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.arch.persistence.room.Room
import android.support.test.InstrumentationRegistry
import android.support.test.filters.MediumTest
import android.support.test.runner.AndroidJUnit4
import com.anthonymandra.rawdroid.XmpFilter
import com.anthonymandra.rawdroid.XmpValues
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertTrue
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasSize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.StringReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


@RunWith(AndroidJUnit4::class)
@MediumTest
class AppDatabaseTest {

    @get:Rule var instantTaskExecutorRule = InstantTaskExecutorRule()

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
    private val testFolder
        get() = FolderEntity(
            "source:folder/file",
            folderId,
            "/" + folderId,
            -1,
            0 )

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
    fun setUp() {
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

        val parent = testFolder

        val id = folderDao.insert(parent)

        assertEquals(folderId, id)    // Can we override the autoGenerate?

        assertFolder(parent)

        val updated = testFolder
        updated.documentUri = "source:/folder/updated_file"

        folderDao.update(updated)
        assertFolder(updated)

        val child = testFolder
        child.id++
        child.path = parent.path + "/" + child.id
        child.parent = parent.id
        child.depth = parent.depth + 1

        val childId = folderDao.insert(child)

        folderDao.delete(updated, child)
        assertEquals(0, folderDao.count().toLong())
    }

    @Test
    fun metadata() {
        val folderId = folderDao.insert(testFolder)
        assertEquals(1, folderDao.count().toLong())

        assertEquals(0, metadataDao.count().toLong())
        val first = getPopulatedMeta(1)

        val imageId = metadataDao.insert(first)

        assertMetadata(first)

        val updated = getPopulatedMeta(2)
        updated.id = imageId

        metadataDao.update(updated)
        assertMetadata(updated)
//
//        val meta = metadataDao.getWithRelations().blockingObserve()

        metadataDao.delete(updated)
        assertEquals(0, metadataDao.count().toLong())
    }

//    @Test
//    fun subjects() {
//        assertThat(subjectDao.count(), equalTo(0))
//
//        val reader = StringReader(testSubjects)
//        subjectDao.importKeywords(reader)
//        assertThat(subjectDao.count(), equalTo(testSubjectsCount))
//
//        val europe = subjectDao.get(7)
//        assertThat(europe.name, equalTo("Europe"))
//
//        val europeanEntities = subjectDao.getDescendants(7)
//        assertThat(europeanEntities, hasSize(4))
//        val europeanNames = europeanEntities.map { it.name }
//        assertThat(europeanNames, hasItems("Europe", "Germany", "Trier", "France"))
//
//        val trier = subjectDao.get(9)
//        assertThat(trier.name, equalTo("Trier"))
//
//        val trierTree = subjectDao.getAncestors(9)
//        assertThat(trierTree, hasSize(3))
//        val trierTreeNames = trierTree.map { it.name }
//        assertThat(trierTreeNames, hasItems("Europe", "Germany", "Trier"))
//
//        val time = System.currentTimeMillis()
//        europeanEntities.forEach( {it.recent = time})
//
//        subjectDao.update(*europeanEntities.toTypedArray())
//
//        val updateEuropeanEntities = subjectDao.getDescendants(7)
//        updateEuropeanEntities.forEach { europeanEntity ->
//            assertThat(europeanEntity.recent, equalTo(time))
//        }
//        subjectDao.deleteAll()
//        assertThat(subjectDao.count(), equalTo(0))
//    }
//
//    @Test
//    fun subjectJunction() {
//        populateFullRelations()
//
//        val imagesWith1 = subjectJunctionDao.getImagesWith(1)
//        val imagesWith2 = subjectJunctionDao.getImagesWith(2)
//
//        val subjectsFor1 = subjectJunctionDao.getSubjectsFor(1)
//        val subjectsFor2 = subjectJunctionDao.getSubjectsFor(2)
//
//        assertThat(imagesWith1, hasItems(1L,2L))
//        assertThat(imagesWith2, hasItems(1L))
//        assertThat(subjectsFor1, hasItems(1L,2L))
//        assertThat(subjectsFor2, hasItems(1L))
//
//        val joinResult = metadataDao.images.blockingObserve()
//
//        // Ensure we don't have separate entities per junction match
//        assertThat(joinResult!!.size, equalTo(2))
//
//        assertThat(joinResult[0].keywords, hasItems("Cathedral", "National Park"))
//        assertThat(joinResult[1].keywords, hasItems("Cathedral"))
//    }
//
    @Test
    fun filter() {
        populateFullRelations()

        val label = "label1"
        val subject = "Cathedral"

        val subjectEntity = SubjectEntity(subject)
        subjectEntity.id = 1L

        val test = metadataDao.test2().blockingObserve()

        // subject: Cathedral
        var xmp = XmpValues(subject = listOf(subjectEntity))
        var filter = XmpFilter(xmp)
        var result = metadataDao.getImages(filter).blockingObserve()
        assertThat(result!!.size, equalTo(2))
        assertThat(result[0].keywords, hasItems(subjectEntity.name))

//        // subject: Cathedral OR label:label1
//        filter.andTrueOrFalse = false
//        xmp = XmpValues(subject = listOf(subjectEntity), label = listOf(label))
//        filter = XmpFilter(xmp)
//        result = metadataDao.getImages(filter).blockingObserve()
//        assertThat(result!!.size, equalTo(2))
//        assertThat(result[0].keywords, hasItems(subject))
//        assertThat(result[1].label, not(label))
//
//        // subject: Cathedral AND label:label1
//        filter.andTrueOrFalse = true
//        result = metadataDao.getImages(filter).blockingObserve()
//        assertThat(result!!.size, equalTo(1))
//        assertThat(result[0].keywords, hasItems(subject))
//        assertThat(result[0].label, equalTo(label))
    }

    private fun populateFullRelations() {
        // Prep parents
        val folderId = folderDao.insert(testFolder)
        assertEquals(1, folderDao.count().toLong())

        // Prep images
        val image1 = getPopulatedMeta(1)
        val image2 = getPopulatedMeta(2)

        val imageId1 = metadataDao.insert(image1)
        val imageId2 = metadataDao.insert(image2)

        // Prep subjects
        val reader = StringReader(testSubjects)
        subjectDao.importKeywords(reader)
        assertThat(subjectDao.count(), equalTo(testSubjectsCount))

        /**
         *          / subject 1 (Cathedral)
         *  Image 1
         *         \ subject 2 (National Park)
         *
         *                       / Image 1
         *  subject 1 (Cathedral)
         *                      \ Image 2
         */
        val subjectRelation1 = SubjectJunction(imageId1, 1)
        val subjectRelation2 = SubjectJunction(imageId1, 2)
        val subjectRelation3 = SubjectJunction(imageId2, 1)

        subjectJunctionDao.insert(subjectRelation1)
        subjectJunctionDao.insert(subjectRelation2)
        subjectJunctionDao.insert(subjectRelation3)
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

    private fun getXmpMeta(): MetadataResult {
        val meta = MetadataResult()
        meta.label = "red"
        meta.keywords = listOf("europe", "germany")
        meta.rating = 1f
        return meta
    }

    private fun getPopulatedMeta(suffix: Int): MetadataEntity {
        val meta = MetadataEntity()
        meta.id = suffix.toLong()
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
        meta.rating = suffix.toFloat()
        meta.parentId = folderId
        meta.name = "image$suffix.cr2"
        meta.documentId = "$treeId/${meta.name}"
        meta.uri = "$host/$tree/$treeId/$document/${meta.documentId}"
        return meta
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