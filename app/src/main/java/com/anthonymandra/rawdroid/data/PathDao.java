package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

@Dao
public abstract class PathDao extends PathBase
{
	protected final static String DATABASE = "Overwrite";

	@Query("SELECT * FROM " + DATABASE + " WHERE " + PathEntity._ID + "= :id ")
	abstract PathEntity getPath(long id);    // private ideal

	@Query("SELECT " + PathEntity._ID + " FROM " + DATABASE +
			" WHERE " + PathEntity.PATH + " LIKE :path || '%'")
	abstract List<Long> getDescendantsInternal(String path);

	@Query("SELECT " + PathEntity._ID + " FROM " + DATABASE +
			" WHERE :path LIKE " + PathEntity.PATH + " || '%'")
	abstract List<Long> getAncestorsInternal(String path);

	@Insert
	abstract Long insertInternal(PathEntity entities);

	@Insert
	abstract void insertInternal(PathEntity... entities);

//	@Insert
//	abstract List<Long> insertInternal(PathEntity... entities);

	@Update
	public abstract void update(PathEntity... entities);

	@Delete
	public abstract void delete(PathEntity... entities);
}
