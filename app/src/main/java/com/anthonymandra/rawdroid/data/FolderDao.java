package com.anthonymandra.rawdroid.data;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Query;

import java.util.List;

@Dao
public abstract class FolderDao extends PathDao
{
	protected final static String DATABASE = FolderEntity.DATABASE;

	@Query("SELECT * FROM " + DATABASE)
	public abstract LiveData<List<FolderEntity>> getAll();
}
