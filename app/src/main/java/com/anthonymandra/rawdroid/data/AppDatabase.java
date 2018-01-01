package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Context;

@Database(entities = {
		FolderEntity.class,
		MetadataEntity.class,
		SubjectEntity.class,
		SubjectJunction.class,
		SynonymEntity.class},
		version = 1)
public abstract class AppDatabase extends RoomDatabase {
	private static final String DB_NAME = "curator.db";
	private static AppDatabase sInstance;

	public static AppDatabase getInstance(final Context context) {
		if (sInstance == null) {
			synchronized (AppDatabase.class) {
				if (sInstance == null) {
					sInstance = create(context.getApplicationContext(), false);
				}
			}
		}
		return sInstance;
	}

	static AppDatabase create(Context ctxt, boolean memoryOnly) {
		RoomDatabase.Builder<AppDatabase> b;

		if (memoryOnly)	//FIXME: Remove this test-only code
		{
			b = Room.inMemoryDatabaseBuilder(ctxt.getApplicationContext(), AppDatabase.class);
		}
		else
		{
			b = Room.databaseBuilder(ctxt.getApplicationContext(), AppDatabase.class, DB_NAME);
		}
		return(b.build());
	}

	public abstract FolderDao folderDao();
	public abstract MetadataDao metadataDao();
	public abstract SubjectDao subjectDao();
	public abstract SubjectJunctionDao subjectJunctionDao();
}