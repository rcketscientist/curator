package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.anthonymandra.content.Meta;

import io.reactivex.Flowable;

@Dao
public abstract class MetadataAccess
{
	private static final String FROM_META = " FROM " + Meta.META;
	private static final String URI_ID = Meta.URI + "," + Meta._ID;
	private static final String URI_NAME = Meta.URI + "," + Meta.NAME;
	private static final String WHERE_UNPROCESSED = Meta.PROCESSED + " IS NULL OR " + Meta.PROCESSED + " = \"\"";
	private static final String WHERE_URI = " WHERE " + Meta.URI;

	@Query("SELECT COUNT(*)" + FROM_META)
	public abstract int count();

	@Query("SELECT " + Meta.URI + "," + Meta._ID + FROM_META)
	public abstract Flowable<UriId> getUriId();

	@Query("SELECT *" + FROM_META)
	public abstract Flowable<Metadata> getAll();

	@Query("SELECT " + URI_NAME + " WHERE " + WHERE_UNPROCESSED + FROM_META)
	public abstract Flowable<String> getUnprocessed();

	@Query("SELECT " + Meta.URI + " WHERE " + " :where " + " ORDER BY " + " :order")
	public abstract Flowable<String> getFiltered(String where, String order);

	@Query("SELECT DISTINCT " + Meta.PARENT + " ORDER BY " + Meta.PARENT + " ASC")
	public abstract Flowable<String> getFolders();

	@Query("SELECT " + Meta.TYPE + WHERE_URI + " = :uri")
	public abstract Flowable<Integer> getType();

	@Query("SELECT " + ":select" +  " WHERE " + ":where" + )
	public abstract Flowable<Metadata> getAll(String select, String where);

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	public abstract long insert(FileInfo file);

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	public abstract long[] insert(FileInfo... files);

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	public abstract long insert(Metadata datum);

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	public abstract long[] insert(Metadata... datums);

	@Delete
	public abstract void delete(Metadata... datums);

	@Delete
	public abstract void delete();

	public Flowable<Metadata> getAll(@Nullable String[] columns, @NonNull Uri[] uri)
	{

	}
}
