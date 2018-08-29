package com.anthonymandra.rawdroid.data

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.test.InstrumentationRegistry
import androidx.test.filters.MediumTest
import androidx.test.runner.AndroidJUnit4
import com.anthonymandra.rawdroid.XmpFilter
import com.anthonymandra.rawdroid.XmpValues
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertTrue
import org.hamcrest.CoreMatchers.*
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
            true,
            false,
            folderId)

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

    val cathedral = SubjectEntity(name = "Cathedral", id = 1L)
    val nationalPark = SubjectEntity("National Park", id = 2L)
    val badlands = SubjectEntity("Badlands", id = 3L)
    val europe = SubjectEntity("Europe", id = 7L)

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
//        child.path = parent.path + "/" + child.id
//        child.parent = parent.id
//        child.depth = parent.depth + 1

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

        metadataDao.delete(updated)
        assertEquals(0, metadataDao.count().toLong())
    }

    @Test
    fun subjects() {
        assertThat(subjectDao.count(), equalTo(0))

        val reader = StringReader(testSubjects)
        subjectDao.importKeywords(reader)
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

        subjectDao.update(*europeanEntities.toTypedArray())

        val updateEuropeanEntities = subjectDao.getDescendants(7)
        updateEuropeanEntities.forEach { europeanEntity ->
            assertThat(europeanEntity.recent, equalTo(time))
        }
        subjectDao.deleteAll()
        assertThat(subjectDao.count(), equalTo(0))
    }

    @Test
    fun subjectJunction() {
        populateFullRelations()

        val imagesWith1 = subjectJunctionDao.getImagesWith(1)
        val imagesWith2 = subjectJunctionDao.getImagesWith(2)
        val imagesWith7 = subjectJunctionDao.getImagesWith(7)

        val subjectsFor1 = subjectJunctionDao.getSubjectsFor(1)
        val subjectsFor2 = subjectJunctionDao.getSubjectsFor(2)
        val subjectsFor3 = subjectJunctionDao.getSubjectsFor(3)

        assertThat(imagesWith1, hasItems(1L,2L))
        assertThat(imagesWith2, hasItems(1L))
        assertThat(imagesWith7, hasItems(3L))

        assertThat(subjectsFor1, hasItems(1L,2L))
        assertThat(subjectsFor2, hasItems(1L))
        assertThat(subjectsFor3, hasItems(7L))


        val joinResult = metadataDao.allMetadata.blockingObserve()

        // Ensure we don't have separate entities per junction match
        assertThat(joinResult!!.size, equalTo(3))

        assertThat(joinResult[0].subjectIds, hasItems(1L, 2L))
        assertThat(joinResult[1].subjectIds, hasItems(1L))
        assertThat(joinResult[2].subjectIds, hasItems(7L))
    }

    @Test
    fun filter() {
        populateFullRelations()

        val label = "label1"

        // subject: Cathedral
        var xmp = XmpValues(subject = listOf(cathedral))
        var filter = XmpFilter(xmp)
        var result = metadataDao.getImages(filter).blockingObserve()
        assertThat(result!!.size, equalTo(2))
        assertThat(result[0].subjectIds, hasItems(cathedral.id))

        // subject: National Park OR Europe
        xmp = XmpValues(subject = listOf(nationalPark, europe))
        filter = XmpFilter(xmp, false)
        result = metadataDao.getImages(filter).blockingObserve()
        assertThat(result!!.size, equalTo(2))
        result.forEach {
            assertThat(it.subjectIds, anyOf(
                    hasItems(nationalPark.id),
                    hasItems(europe.id)))
        }

        // subject: Cathedral OR label:label1
        xmp = XmpValues(subject = listOf(europe), label = listOf(label))
        filter = XmpFilter(xmp, false)

        result = metadataDao.getImages(filter).blockingObserve()
        assertThat(result!!.size, equalTo(2))
        result.forEach {
            assert(it.subjectIds.contains(europe.id) || it.label == label)
        }

        // subject: Cathedral AND label:label1
        xmp = XmpValues(subject = listOf(cathedral), label = listOf(label))
        filter = XmpFilter(xmp, true)

        result = metadataDao.getImages(filter).blockingObserve()
        assertThat(result!!.size, equalTo(1))
        result.forEach {
            assert(it.subjectIds.contains(cathedral.id) || it.label == label)
        }
    }

    private fun populateFullRelations() {
        // Prep parents
        val folderId = folderDao.insert(testFolder)
        assertEquals(1, folderDao.count().toLong())

        // Prep images
        val image1 = getPopulatedMeta(1)
        val image2 = getPopulatedMeta(2)
        val image3 = getPopulatedMeta(3)

        val imageId1 = metadataDao.insert(image1)
        val imageId2 = metadataDao.insert(image2)
        val imageId3 = metadataDao.insert(image3)

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
         *
         *  Image3 is the outlier with just Badlands (3)
         */
        val subjectRelation1 = SubjectJunction(imageId1, 1) // 1: Cathedral, National Park
        val subjectRelation2 = SubjectJunction(imageId1, 2) // ---
        val subjectRelation3 = SubjectJunction(imageId2, 1) // 2: Cathedral
        val subjectRelation4 = SubjectJunction(imageId3, 7) // 3: Europe

        subjectJunctionDao.insert(subjectRelation1)
        subjectJunctionDao.insert(subjectRelation2)
        subjectJunctionDao.insert(subjectRelation3)
        subjectJunctionDao.insert(subjectRelation4)
    }

    private fun assertFolder(entity: FolderEntity) {
        val results = folderDao.lifecycleParents.blockingObserve()

        assertNotNull(results)
        assertEquals(1, results!!.size.toLong())
        assertTrue(entity == results[0])

        val result = folderDao.get(entity.id)

        assertNotNull(result)
        assertTrue(result == entity)
    }

    private fun assertMetadata(entity: MetadataEntity) {
        val results = metadataDao.allImages.blockingObserve()

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

    private fun getPopulatedMeta(suffix: Int): MetadataEntity {
        val meta = MetadataEntity()
        meta.id = suffix.toLong()
        meta.altitude = "altitude$suffix"
        meta.aperture = "aperture$suffix"
        meta.driveMode = "driveMode$suffix"
        meta.exposure = "exposure$suffix"
        meta.exposureMode = "exposureMode$suffix"
        meta.exposureProgram = "exposureProgram$suffix"
        meta.flash = "flash$suffix"
        meta.focalLength = "focalLength$suffix"
        meta.height = suffix
        meta.width = suffix
        meta.iso = "iso$suffix"
        meta.latitude = "latitude$suffix"
        meta.lens = "lens$suffix"
        meta.longitude = "longitude$suffix"
        meta.make = "make$suffix"
        meta.orientation = suffix
        meta.timestamp = 1337
        meta.model = "model$suffix"
        meta.whiteBalance = "whiteBalance$suffix"
        meta.label = "label$suffix"
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