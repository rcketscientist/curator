package com.anthonymandra.rawdroid.data;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.net.Uri;

import com.anthonymandra.content.Meta;
import com.anthonymandra.rawdroid.XmpFilter;

import java.util.Arrays;
import java.util.List;

@Dao
public abstract class MetadataAccess
{
	private static final String FROM_META = " FROM " + Meta.META;

	private static final List<String> URI_ID = Arrays.asList(Meta.URI, Meta._ID);
	private static final String URI_NAME = Meta.URI + "," + Meta.NAME;
	private static final List<String> XMP =Arrays.asList(Meta.URI, Meta.RATING, Meta.SUBJECT, Meta.LABEL);
	private static final String WHERE_UNPROCESSED = Meta.PROCESSED + " IS NULL OR " + Meta.PROCESSED + " = \"\"";
	private static final String WHERE_URI = " WHERE " + Meta.URI;

	@Query("SELECT COUNT(*)" + FROM_META)
	public abstract int count();

	@Query("SELECT :URI_ID" + FROM_META)
	public abstract LiveData<List<UriId>> getUriId();

	@Query("SELECT *" + FROM_META)
	public abstract LiveData<List<Metadata>> getAll();

	@Query("SELECT " + GalleryImage.SELECT +
			" WHERE " + Meta.LABEL + " IN (:labels) :andOr :subjectsLikeClause :andOR" +
			Meta.RATING + " IN (:ratings) AND :foldersLikeClause :orderClause" + FROM_META)
	public abstract LiveData<List<GalleryImage>> getGalleryImages(
			List<String> labels,
			String subjectsLikeClause,
			String foldersLikeClause,
			List<Integer> ratings,
			String andOr,
			String orderClause);

	public LiveData<List<GalleryImage>> getGalleryImages(List<String> labels,
	                                                     String subjectsLikeClause,
	                                                     String foldersLikeClause,
	                                                     List<Integer> ratings,
	                                                     String andOr,
	                                                     String orderClause)
	{
		String subjectClause =
	}

	@Query("SELECT :URI_NAME" + " WHERE " + WHERE_UNPROCESSED + FROM_META)
	public abstract LiveData<List<UriName>> getUnprocessed();

	@Query("SELECT " + Meta.URI + " WHERE " + " :where " + " ORDER BY " + " :order" + FROM_META)
	public abstract LiveData<List<String>> getFilteredUri(String where, String order);

	@Query("SELECT DISTINCT " + Meta.PARENT + " ORDER BY " + Meta.PARENT + " ASC" + FROM_META)
	public abstract LiveData<List<String>> getFolders();

	@Query("SELECT " + Meta.TYPE + WHERE_URI + " = :uri" + FROM_META)
	public abstract LiveData<Integer> getType();

	@Query("SELECT " + ":select" +  " WHERE " + ":where" + FROM_META)
	public abstract LiveData<List<Metadata>> get(String select, String where);

	@Query("SELECT * " + " WHERE " + Meta.URI + " IN (:uris)" + FROM_META)
	public abstract LiveData<List<Metadata>> getAll(List<Uri> uris);

	@Query("SELECT " + XmpResult.SELECT + " WHERE " + Meta.URI + " IN (:uris)" + FROM_META)
	public abstract LiveData<List<XmpResult>> getXmp(List<Uri> uris);

	@Query("SELECT " + ":select" +  " WHERE " + ":where" + " ORDER BY " + " :order" + FROM_META)
	public abstract LiveData<List<Metadata>> get(String select, String where, String order);

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

	public static String createLike(List<String> likes, boolean not, boolean and)
	{
		String LIKE = not ? " NOT LIKE " : " LIKE ";
		String JOIN = and ? " AND " : " OR ";
		StringBuilder clause = new StringBuilder();

		for (String like : likes)
		{
			clause.append(LIKE + " " + like + JOIN);
		}

		String result = clause.toString();
		result = result.replaceAll(JOIN + "$", "");
		return result;
	}

	public static String createOrderClause(boolean segregate, XmpFilter.SortColumns sortColumn)
	{
		StringBuilder order = new StringBuilder();
		order.append(" ORDER BY ");
		if (segregate)
			order.append(Meta.TYPE + " COLLATE NOCASE ASC, ");

		switch (sortColumn)
		{
			case Date: order.append(Meta.TIMESTAMP).append(order); break;
			case Name: order.append(Meta.NAME).append(" COLLATE NOCASE").append(order); break;
			default: order.append(Meta.NAME).append(" COLLATE NOCASE").append(" ASC");
		}
		return order.toString();
	}

//	public class Converters {
//		@TypeConverter
//		public static Date fromTimestamp(Long value) {
//			return value == null ? null : new Date(value);
//		}
//
//		@TypeConverter
//		public static Long dateToTimestamp(Date date) {
//			return date == null ? null : date.getTime();
//		}
//	}
}
