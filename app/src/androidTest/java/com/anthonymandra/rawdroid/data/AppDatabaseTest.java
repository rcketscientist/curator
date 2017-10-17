package com.anthonymandra.rawdroid.data;

import android.support.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;


public class AppDatabaseTest
{
	AppDatabase db;
	FolderDao folderDao;
	MetadataDao metadataDao;
	SubjectDao subjectDao;

	@Before
	public void setUp() throws Exception
	{
		db=AppDatabase.create(InstrumentationRegistry.getTargetContext(), true);
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
	public void loadFolders() {
		assertNotNull(folderDao.getAll().getValue());   //TODO: Does zero = null return?
		assertEquals(0, folderDao.getAll().getValue().size());
		final FolderEntity first = new FolderEntity(
				"source:folder/file", 0, "/0", 0);

		folderDao.insert(first);

		assertNotNull(first.id);    //TODO: Does this update?

		List<FolderEntity> results = folderDao.getAll().getValue();
		assertNotNull(results);
		assertEquals(1, results.size());
		assertTrue(first.equals(results.get(0)));

		assertTrue(areIdentical(trip, results.get(0)));


		assertTrip(store, first);
		final Trip updated=new Trip(first.id, "Foo!!!", 1440);
		store.update(updated);
		assertTrip(store, updated);
		store.delete(updated);
		assertEquals(0, store.selectAll().size());
	}


	@Test
	public void basics() {
		assertEquals(0, store.selectAll().size());
		final Trip first=new Trip("Foo", 2880);
		assertNotNull(first.id);
		assertNotEquals(0, first.id.length());
		store.insert(first);
		assertTrip(store, first);
		final Trip updated=new Trip(first.id, "Foo!!!", 1440);
		store.update(updated);
		assertTrip(store, updated);
		store.delete(updated);
		assertEquals(0, store.selectAll().size());
	}

}