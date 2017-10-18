package com.anthonymandra.rawdroid.data;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Query;

import java.util.List;

@Dao
public abstract class FolderDao extends PathDao
{
	protected final static String DATABASE = FolderEntity.DATABASE;

	@Query("SELECT COUNT(*) FROM " + DATABASE)
	public abstract int count();

	@Query("SELECT * FROM " + DATABASE)
	public abstract LiveData<List<FolderEntity>> getAll();

	@Query("SELECT * FROM " + DATABASE + " WHERE " + FolderEntity._ID + " = :id")
	public abstract LiveData<FolderEntity> get(long id);
}
