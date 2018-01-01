package com.anthonymandra.rawdroid.data;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

@Dao
public abstract class FolderDao extends PathDao<FolderEntity>
{
	@Query("SELECT COUNT(*) FROM image_parent")
	public abstract int count();

	@Query("SELECT * FROM image_parent")
	public abstract LiveData<List<FolderEntity>> getAll();

	@Query("SELECT * FROM image_parent WHERE id = :id")
	public abstract FolderEntity get(long id);

	@Query("SELECT * FROM image_parent WHERE id = :id")
	abstract FolderEntity get(Long id);    // private ideal

	@Query("SELECT id FROM image_parent WHERE path LIKE :path || '%'")
	public abstract List<Long> getDescendantIds(String path);

	@Query("SELECT id FROM image_parent WHERE :path LIKE path || '%'")
	public abstract List<Long> getAncestorIds(String path);

	@Insert
	abstract Long insertInternal(FolderEntity entities);

	@Update
	public abstract void update(FolderEntity entities);

	@Update
	public abstract void update(FolderEntity... entities);

	@Delete
	public abstract void delete(FolderEntity... entities);
}
