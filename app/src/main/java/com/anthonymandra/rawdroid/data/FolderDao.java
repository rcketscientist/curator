package com.anthonymandra.rawdroid.data;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

@Dao
public abstract class FolderDao extends PathBase
{
	protected final static String DATABASE = FolderEntity.DATABASE;

	@Query("SELECT COUNT(*) FROM " + DATABASE)
	public abstract int count();

	@Query("SELECT * FROM " + DATABASE)
	public abstract LiveData<List<FolderEntity>> getAll();

	@Query("SELECT * FROM " + DATABASE + " WHERE " + FolderEntity._ID + " = :id")
	public abstract LiveData<FolderEntity> get(long id);

	@Query("SELECT * FROM " + DATABASE + " WHERE " + FolderEntity._ID + "= :id ")
	abstract FolderEntity getPath(long id);    // private ideal

	@Query("SELECT " + FolderEntity._ID + " FROM " + DATABASE +
			" WHERE " + FolderEntity.PATH + " LIKE :path || '%'")
	abstract List<Long> getDescendantsInternal(String path);

	@Query("SELECT " + FolderEntity._ID + " FROM " + DATABASE +
			" WHERE :path LIKE " + FolderEntity.PATH + " || '%'")
	abstract List<Long> getAncestorsInternal(String path);

	@Insert
	abstract Long insertInternal(FolderEntity entities);

	@Insert
	abstract void insertInternal(FolderEntity... entities);

//	@Insert
//	abstract List<Long> insertInternal(PathEntity... entities);

	@Update
	public abstract void update(FolderEntity... entities);

	@Delete
	public abstract void delete(FolderEntity... entities);
}
