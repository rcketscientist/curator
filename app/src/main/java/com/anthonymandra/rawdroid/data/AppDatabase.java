package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;

import com.anthonymandra.rawdroid.data.dao.FolderDao;
import com.anthonymandra.rawdroid.data.dao.MetadataDao;
import com.anthonymandra.rawdroid.data.dao.SubjectDao;
import com.anthonymandra.rawdroid.data.entity.FolderEntity;
import com.anthonymandra.rawdroid.data.entity.MetadataEntity;
import com.anthonymandra.rawdroid.data.entity.SubjectEntity;
import com.anthonymandra.rawdroid.data.entity.SynonymEntity;

@Database(entities = {
		FolderEntity.class,
		MetadataEntity.class,
		SubjectEntity.class,
		SynonymEntity.class},
		version = 1)
public abstract class AppDatabase extends RoomDatabase {
	public abstract FolderDao folderDao();
	public abstract MetadataDao metadataDao();
	public abstract SubjectDao subjectDao();
}