package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;

import com.anthonymandra.content.Meta;

@Dao
public interface MetadataAccess
{
	@Query("SELECT COUNT(*) FROM " + Meta.META)
	int count;





	@Insert(onConflict = OnConflictStrategy.REPLACE)
	void insert(FileInfo... files);

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	void insert(Metadata... datums);

	@Delete
	void delete(Metadata... datums);

	@Delete
	void delete()

}
