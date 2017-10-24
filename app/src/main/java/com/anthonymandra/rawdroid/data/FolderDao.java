package com.anthonymandra.rawdroid.data;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

@Dao
public abstract class FolderDao extends PathBase<FolderEntity>
{
	protected final static String DATABASE = FolderEntity.DATABASE;

	@Override
	String getDatabase()
	{
		return DATABASE;
	}

	@Query("SELECT COUNT(*) FROM " + DATABASE)
	public abstract int count();

	@Query("SELECT * FROM " + DATABASE)
	public abstract LiveData<List<FolderEntity>> getAll();

	@Query("SELECT * FROM " + DATABASE + " WHERE " + FolderEntity._ID + " = :id")
	public abstract LiveData<FolderEntity> get(long id);

	@Query("SELECT * FROM " + DATABASE + " WHERE " + FolderEntity._ID + "= :id ")
	abstract FolderEntity getPath(Long id);    // private ideal

	@Query("SELECT " + PathEntity._ID + " FROM " + DATABASE +
			" WHERE " + PathEntity.PATH + " LIKE :path || '%'")
	public abstract List<Long> getDescendants(String path);

	@Query("SELECT " + PathEntity._ID + " FROM " + DATABASE +
			" WHERE :path LIKE " + PathEntity.PATH + " || '%'")
	public abstract List<Long> getAncestors(String path);

	@Insert
	abstract Long insertInternal(FolderEntity entities);

	@Update
	public abstract void update(FolderEntity... entities);

	@Delete
	public abstract void delete(FolderEntity... entities);
}
