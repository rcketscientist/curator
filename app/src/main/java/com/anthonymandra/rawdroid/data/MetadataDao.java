package com.anthonymandra.rawdroid.data;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;
import android.net.Uri;

import com.anthonymandra.content.Meta;
import com.anthonymandra.rawdroid.XmpFilter;

import java.util.List;

@Dao
public abstract class MetadataDao
{
	private static final String FROM_META = " FROM " + Meta.META;
	private static final String WHERE_UNPROCESSED = Meta.PROCESSED + " IS NULL OR " + Meta.PROCESSED + " = \"\"";
	private static final String WHERE_URI = " WHERE " + Meta.URI;

	@Query("SELECT COUNT(*)" + FROM_META)
	public abstract int count();

	@Query("SELECT " + UriIdResult.SELECT + FROM_META)
	public abstract LiveData<List<UriIdResult>> getUriId();

	@Query("SELECT *" + FROM_META)
	public abstract LiveData<List<MetadataEntity>> getAll();

//	@Query("SELECT " + GalleryResult.SELECT +
//			" WHERE " + Meta.LABEL + " IN (:labels) " +
//			"AND :subjectsLikeClause " +
//			"AND " + Meta.RATING + " IN (:ratings) " +
//			"AND :foldersLikeClause " +
//			"ORDER BY :orderClause FROM " + Meta.META)
//	abstract LiveData<List<GalleryResult>> getGalleryImagesAND(
//			List<String> labels,
//			String subjectsLikeClause,
//			String foldersLikeClause,
//			List<Integer> ratings,
//			String orderClause);

//	@Query("SELECT " + GalleryResult.SELECT +
//			" WHERE " + Meta.LABEL + " IN (:labels) " +
//			"OR :subjectsLikeClause " +
//			"OR " + Meta.RATING + " IN (:ratings) " +
//			"AND :foldersLikeClause " +
//			"ORDER BY :orderClause" + FROM_META)
//	abstract LiveData<List<GalleryResult>> getGalleryImagesOR(
//			List<String> labels,
//			String subjectsLikeClause,
//			String foldersLikeClause,
//			List<Integer> ratings,
//			String orderClause);

//	public LiveData<List<GalleryResult>> getGalleryImages(
//		List<String> labels,
//		List<String> subjects,
//		List<String> folders,
//		List<Integer> ratings,
//		boolean and,
//		boolean segregate,
//		boolean ascending,
//		XmpFilter.SortColumns sortBy)
//	{
//		String subjectClause = createLike(subjects, false, and, "%", "%");
//		String folderClause = createLike(folders, true, true, null, "%");
//		String orderClause = createOrderClause(segregate, sortBy, ascending);
//
//		if (and)
//			return getGalleryImagesAND(labels, subjectClause, folderClause, ratings, orderClause);
//		else
//			return getGalleryImagesOR(labels, subjectClause, folderClause, ratings, orderClause);
//	}

	@Query("SELECT " + UriNameResult.SELECT + " WHERE " + WHERE_UNPROCESSED + FROM_META)
	public abstract LiveData<List<UriNameResult>> getUnprocessed();

	@Query("SELECT " + Meta.URI + " WHERE " + " :where " + " ORDER BY " + " :order" + FROM_META)
	public abstract LiveData<List<String>> getFilteredUri(String where, String order);

	@Query("SELECT DISTINCT " + Meta.PARENT + " ORDER BY " + Meta.PARENT + " ASC" + FROM_META)
	public abstract LiveData<List<String>> getFolders();

	@Query("SELECT " + Meta.TYPE + WHERE_URI + " = :uri" + FROM_META)
	public abstract LiveData<Integer> getType();

	@Query("SELECT * " +  " WHERE " + Meta._ID + " = :id " + FROM_META)
	public abstract LiveData<MetadataEntity> get(long id);

	@Query("SELECT " + ":select" +  " WHERE " + ":where" + FROM_META)
	public abstract LiveData<List<MetadataEntity>> get(String select, String where);

	@Query("SELECT * " + " WHERE " + Meta.URI + " IN (:uris)" + FROM_META)
	public abstract LiveData<List<MetadataEntity>> getAll(List<Uri> uris);

	@Query("SELECT " + XmpResult.SELECT + " WHERE " + Meta.URI + " IN (:uris)" + FROM_META)
	public abstract LiveData<List<XmpResult>> getXmp(List<Uri> uris);

	@Query("SELECT " + ":select" +  " WHERE " + ":where" + " ORDER BY " + " :order" + FROM_META)
	public abstract LiveData<List<MetadataEntity>> get(String select, String where, String order);

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	public abstract long insert(FileInfo file);

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	public abstract long[] insert(FileInfo... files);

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	public abstract long insert(MetadataEntity datum);

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	public abstract long[] insert(MetadataEntity... datums);

	@Update
	public abstract void update(MetadataEntity... datums);

	@Delete
	public abstract void delete(MetadataEntity... datums);

	@Delete
	public abstract void delete();

	public static String createLike(List<String> likes, boolean not, boolean and, String preWild, String postWild)
	{
		String LIKE = not ? " NOT LIKE " : " LIKE ";
		String JOIN = and ? " AND " : " OR ";
		StringBuilder clause = new StringBuilder();

		for (String like : likes)
		{
			if(preWild != null) clause.append(preWild);
			clause.append(LIKE + " " + like + JOIN);
			if(postWild != null) clause.append(postWild);
		}

		String result = clause.toString();
		result = result.replaceAll(JOIN + "$", "");
		return result;
	}

	public static String createOrderClause(boolean segregate, XmpFilter.SortColumns sortColumn, boolean ascending)
	{
		// TODO: We could technically do this in an annotation
		String order = ascending ? " ASC" : " DESC";
		StringBuilder sort = new StringBuilder();
		if (segregate)
			sort.append(Meta.TYPE + " COLLATE NOCASE ASC, ");

		switch (sortColumn)
		{
			// TODO: This is the data, move the enum here
			case Date: sort.append(Meta.TIMESTAMP).append(order); break;
			case Name: sort.append(Meta.NAME).append(" COLLATE NOCASE").append(order); break;
			default: sort.append(Meta.NAME).append(" COLLATE NOCASE").append(" ASC");
		}
		return sort.toString();
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
