package com.anthonymandra.rawdroid.data;

import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class AppDatabaseTest
{
	private AppDatabase db;
	private FolderDao folderDao;
	private MetadataDao metadataDao;
	private SubjectDao subjectDao;

	final long folderId = 16;

	@Before
	public void setUp() throws Exception
	{
		db = AppDatabase.create(InstrumentationRegistry.getTargetContext(), true);
		folderDao = db.folderDao();
		metadataDao = db.metadataDao();
		subjectDao = db.subjectDao();
	}

	@After
	public void tearDown()
	{
		db.close();
	}

	@Test
	public void folders() {
		assertEquals(0, folderDao.count());

		final FolderEntity first = new FolderEntity(
				"source:folder/file",
				folderId,
				"/16",
				0,
				null);

		long id = folderDao.insert(first);

		assertEquals(folderId, id);	// Can we override the autoGenerate?

		assertFolder(first);

		final FolderEntity updated = new FolderEntity(
				first.documentId,
				first.id,
				first.path,
				first.depth,
				null);

		updated.documentId = "source:/folder/updated_file";
		folderDao.update(updated);
		assertFolder(updated);

		final FolderEntity child = new FolderEntity(
				first.documentId,
				first.id+1,
				"/16/17",
				first.depth,
				first.id);

		long childId = folderDao.insert(child);

		folderDao.delete(updated, child);
		assertEquals(0, folderDao.count());
	}

	private void assertFolder(FolderEntity entity) {
		List<FolderEntity> results = folderDao.getAll();

		assertNotNull(results);
		assertEquals(1, results.size());
		assertTrue(entity.equals(results.get(0)));

		FolderEntity result = folderDao.get(entity.id);

		assertNotNull(result);
		assertTrue(result.equals(entity));
	}

	@Test
	public void metadata() {
		assertEquals(0, metadataDao.count());
		final MetadataEntity first = getTestData(false);

		metadataDao.insert(first);

		assertMetadata(first);

		final MetadataEntity updated = getTestData(true);

		metadataDao.update(updated);
		assertMetadata(updated);

		metadataDao.delete(updated);
		assertEquals(0, metadataDao.count());
	}
	
	private MetadataEntity getTestData(boolean update) {
		final MetadataEntity meta = new MetadataEntity();
		meta.altitude = update ? "altitude" : "altitude2";
		meta.aperture = update ? "aperture" : "aperture2";
		meta.driveMode = update ? "driveMode" : "driveMode2";
		meta.exposure = update ? "exposure" : "exposure2";
		meta.exposureMode = update ? "exposureMode" : "exposureMode2";
		meta.exposureProgram = update ? "exposureProgram" : "exposureProgram2";
		meta.flash = update ? "flash" : "flash2";
		meta.focalLength = update ? "focalLength" : "focalLength2";
		meta.height = update ? 1080 : 2160;
		meta.width = update ? 1920 : 3840;
		meta.iso = update ? "iso" : "iso2";
		meta.latitude = update ? "latitude" : "latitude2";
		meta.lens = update ? "lens" : "lens2";
		meta.longitude = update ? "longitude" : "longitude2";
		meta.make = update ? "make" : "make2";
		meta.orientation = update ? 12 : 24;
		meta.timestamp = update ? "timestamp" : "timestamp2";  //TODO: long?
		meta.model = update ? "model" : "model2";
		meta.whiteBalance = update ? "whiteBalance" : "whiteBalance2";
		meta.label = update ? "label" : "label2";
		meta.rating = update ? "rating" : "rating2";
		meta.parent = folderId;
//		meta.subject = update ? "subject" : "subject2";
		return meta;
	}

	private void assertMetadata(MetadataEntity entity) {
		List<MetadataEntity> results = metadataDao.getAll().getValue();

		assertNotNull(results);
		assertEquals(1, results.size());
		assertMetaEquality(results.get(0), entity);
		
		MetadataEntity result = metadataDao.get(entity.id).getValue();

		assertNotNull(result);
		assertMetaEquality(result, entity);
	}


	private void assertMetaEquality(MetadataEntity one, MetadataEntity two) {
		assertTrue(
		one.altitude.equals(two.altitude) &&
		one.aperture.equals(two.aperture) &&
		one.driveMode.equals(two.driveMode) &&
		one.exposure.equals(two.exposure) &&
		one.exposureMode.equals(two.exposureMode) &&
		one.exposureProgram.equals(two.exposureProgram) &&
		one.flash.equals(two.flash) &&
		one.focalLength.equals(two.focalLength) &&
		one.height == two.height &&
		one.width == two.width &&
		one.iso.equals(two.iso) &&
		one.latitude.equals(two.latitude) &&
		one.lens.equals(two.lens) &&
		one.longitude.equals(two.longitude) &&
		one.make.equals(two.make) &&
		one.orientation == two.orientation &&
		one.timestamp.equals(two.timestamp) &&
		one.model.equals(two.model) &&
		one.whiteBalance.equals(two.whiteBalance) &&
		one.label.equals(two.label) &&
		one.rating.equals(two.rating)); /*&&
		one.subject.equals(two.subject));*/
	}
}