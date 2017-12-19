package com.anthonymandra.rawdroid.data;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import com.anthonymandra.content.Meta;
import com.anthonymandra.rawdroid.XmpFilter;

import java.util.List;

@Dao
public abstract class MetadataDao
{
	private static final String WHERE_UNPROCESSED = Meta.PROCESSED + " IS NULL OR " + Meta.PROCESSED + " = \"\"";
	private static final String WHERE_URI = " WHERE " + Meta.URI;

	@Query("SELECT COUNT(*) FROM " + Meta.META)
	public abstract int count();

	@Query("SELECT " + UriIdResult.SELECT + " FROM " + Meta.META)
	public abstract LiveData<List<UriIdResult>> getUriId();

	@Query("SELECT * FROM " + Meta.META)
	public abstract LiveData<List<MetadataEntity>> getAll();

// --- AND ----
// --- NAME ---

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parent = image_parent._id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.meta_id = meta_subject_junciton.meta_id " +
			"WHERE meta.label IN (:labels) " +
			"AND meta.rating IN (:ratings) " +
			"AND meta_subject_junciton.subject_id IN (:subjectIds)" +
			"AND meta.parent NOT IN (:hiddenFolderIds)" +
			"ORDER BY Meta.type COLLATE NOCASE ASC, meta.name COLLATE NOCASE ASC")
	abstract LiveData<List<GalleryResult>> getImages_AND_SEG_NAME_ASC(
			List<String> labels,
			List<Long> subjectIds,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parent = image_parent._id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.meta_id = meta_subject_junciton.meta_id " +
			"WHERE meta.label IN (:labels) " +
			"AND meta.rating IN (:ratings) " +
			"AND meta_subject_junciton.subject_id IN (:subjectIds)" +
			"AND meta.parent NOT IN (:hiddenFolderIds)" +
			"ORDER BY Meta.type COLLATE NOCASE ASC, meta.name COLLATE NOCASE DESC")
	abstract LiveData<List<GalleryResult>> getImages_AND_SEG_NAME_DESC(
			List<String> labels,
			List<Long> subjectIds,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parent = image_parent._id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.meta_id = meta_subject_junciton.meta_id " +
			"WHERE meta.label IN (:labels) " +
			"AND meta.rating IN (:ratings) " +
			"AND meta_subject_junciton.subject_id IN (:subjectIds)" +
			"AND meta.parent NOT IN (:hiddenFolderIds)" +
			"ORDER BY meta.name COLLATE NOCASE ASC")
	abstract LiveData<List<GalleryResult>> getImages_AND_NAME_ASC(
			List<String> labels,
			List<Long> subjectIds,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parent = image_parent._id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.meta_id = meta_subject_junciton.meta_id " +
			"WHERE meta.label IN (:labels) " +
			"AND meta.rating IN (:ratings) " +
			"AND meta_subject_junciton.subject_id IN (:subjectIds)" +
			"AND meta.parent NOT IN (:hiddenFolderIds)" +
			"ORDER BY meta.name COLLATE NOCASE DESC")
	abstract LiveData<List<GalleryResult>> getImages_AND_NAME_DESC(
			List<String> labels,
			List<Long> subjectIds,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

//---- TIME ----

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parent = image_parent._id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.meta_id = meta_subject_junciton.meta_id " +
			"WHERE meta.label IN (:labels) " +
			"AND meta.rating IN (:ratings) " +
			"AND meta_subject_junciton.subject_id IN (:subjectIds)" +
			"AND meta.parent NOT IN (:hiddenFolderIds)" +
			"ORDER BY Meta.type COLLATE NOCASE ASC, meta.timestamp COLLATE NOCASE ASC")
	abstract LiveData<List<GalleryResult>> getImages_AND_SEG_TIME_ASC(
			List<String> labels,
			List<Long> subjectIds,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parent = image_parent._id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.meta_id = meta_subject_junciton.meta_id " +
			"WHERE meta.label IN (:labels) " +
			"AND meta.rating IN (:ratings) " +
			"AND meta_subject_junciton.subject_id IN (:subjectIds)" +
			"AND meta.parent NOT IN (:hiddenFolderIds)" +
			"ORDER BY Meta.type COLLATE NOCASE ASC, meta.timestamp COLLATE NOCASE DESC")
	abstract LiveData<List<GalleryResult>> getImages_AND_SEG_TIME_DESC(
			List<String> labels,
			List<Long> subjectIds,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parent = image_parent._id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.meta_id = meta_subject_junciton.meta_id " +
			"WHERE meta.label IN (:labels) " +
			"AND meta.rating IN (:ratings) " +
			"AND meta_subject_junciton.subject_id IN (:subjectIds)" +
			"AND meta.parent NOT IN (:hiddenFolderIds)" +
			"ORDER BY meta.timestamp COLLATE NOCASE ASC")
	abstract LiveData<List<GalleryResult>> getImages_AND_TIME_ASC(
			List<String> labels,
			List<Long> subjectIds,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parent = image_parent._id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.meta_id = meta_subject_junciton.meta_id " +
			"WHERE meta.label IN (:labels) " +
			"AND meta.rating IN (:ratings) " +
			"AND meta_subject_junciton.subject_id IN (:subjectIds)" +
			"AND meta.parent NOT IN (:hiddenFolderIds)" +
			"ORDER BY meta.timestamp COLLATE NOCASE DESC")
	abstract LiveData<List<GalleryResult>> getImages_AND_TIME_DESC(
			List<String> labels,
			List<Long> subjectIds,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

//--- OR ----
//--- NAME ---

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parent = image_parent._id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.meta_id = meta_subject_junciton.meta_id " +
			"WHERE meta.label IN (:labels) " +
			"OR meta.rating IN (:ratings) " +
			"OR meta_subject_junciton.subject_id IN (:subjectIds)" +
			"AND meta.parent NOT IN (:hiddenFolderIds)" +	// Always exclude folders
			"ORDER BY Meta.type COLLATE NOCASE ASC, meta.name COLLATE NOCASE ASC")
	abstract LiveData<List<GalleryResult>> getImages_OR_SEG_NAME_ASC(
			List<String> labels,
			List<Long> subjectIds,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parent = image_parent._id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.meta_id = meta_subject_junciton.meta_id " +
			"WHERE meta.label IN (:labels) " +
			"OR meta.rating IN (:ratings) " +
			"OR meta_subject_junciton.subject_id IN (:subjectIds)" +
			"AND meta.parent NOT IN (:hiddenFolderIds)" +	// Always exclude folders
			"ORDER BY Meta.type COLLATE NOCASE ASC, meta.name COLLATE NOCASE DESC")
	abstract LiveData<List<GalleryResult>> getImages_OR_SEG_NAME_DESC(
			List<String> labels,
			List<Long> subjectIds,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parent = image_parent._id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.meta_id = meta_subject_junciton.meta_id " +
			"WHERE meta.label IN (:labels) " +
			"OR meta.rating IN (:ratings) " +
			"OR meta_subject_junciton.subject_id IN (:subjectIds)" +
			"AND meta.parent NOT IN (:hiddenFolderIds)" +	// Always exclude folders
			"ORDER BY meta.name COLLATE NOCASE ASC")
	abstract LiveData<List<GalleryResult>> getImages_OR_NAME_ASC(
			List<String> labels,
			List<Long> subjectIds,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parent = image_parent._id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.meta_id = meta_subject_junciton.meta_id " +
			"WHERE meta.label IN (:labels) " +
			"OR meta.rating IN (:ratings) " +
			"OR meta_subject_junciton.subject_id IN (:subjectIds)" +
			"AND meta.parent NOT IN (:hiddenFolderIds)" +	// Always exclude folders
			"ORDER BY meta.name COLLATE NOCASE DESC")
	abstract LiveData<List<GalleryResult>> getImages_OR_NAME_DESC(
			List<String> labels,
			List<Long> subjectIds,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

//--- TIME ---

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parent = image_parent._id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.meta_id = meta_subject_junciton.meta_id " +
			"WHERE meta.label IN (:labels) " +
			"OR meta.rating IN (:ratings) " +
			"OR meta_subject_junciton.subject_id IN (:subjectIds)" +
			"AND meta.parent NOT IN (:hiddenFolderIds)" +	// Always exclude folders
			"ORDER BY Meta.type COLLATE NOCASE ASC, meta.timestamp COLLATE NOCASE ASC")
	abstract LiveData<List<GalleryResult>> getImages_OR_SEG_TIME_ASC(
			List<String> labels,
			List<Long> subjectIds,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parent = image_parent._id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.meta_id = meta_subject_junciton.meta_id " +
			"WHERE meta.label IN (:labels) " +
			"OR meta.rating IN (:ratings) " +
			"OR meta_subject_junciton.subject_id IN (:subjectIds)" +
			"AND meta.parent NOT IN (:hiddenFolderIds)" +	// Always exclude folders
			"ORDER BY Meta.type COLLATE NOCASE ASC, meta.timestamp COLLATE NOCASE DESC")
	abstract LiveData<List<GalleryResult>> getImages_OR_SEG_TIME_DESC(
			List<String> labels,
			List<Long> subjectIds,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parent = image_parent._id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.meta_id = meta_subject_junciton.meta_id " +
			"WHERE meta.label IN (:labels) " +
			"OR meta.rating IN (:ratings) " +
			"OR meta_subject_junciton.subject_id IN (:subjectIds)" +
			"AND meta.parent NOT IN (:hiddenFolderIds)" +	// Always exclude folders
			"ORDER BY meta.timestamp COLLATE NOCASE ASC")
	abstract LiveData<List<GalleryResult>> getImages_OR_TIME_ASC(
			List<String> labels,
			List<Long> subjectIds,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parent = image_parent._id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.meta_id = meta_subject_junciton.meta_id " +
			"WHERE meta.label IN (:labels) " +
			"OR meta.rating IN (:ratings) " +
			"OR meta_subject_junciton.subject_id IN (:subjectIds)" +
			"AND meta.parent NOT IN (:hiddenFolderIds)" +	// Always exclude folders
			"ORDER BY meta.timestamp COLLATE NOCASE DESC")
	abstract LiveData<List<GalleryResult>> getImages_OR_TIME_DESC(
			List<String> labels,
			List<Long> subjectIds,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query("SELECT * FROM " + Meta.META +  " WHERE " + Meta._ID + " = :id")
	public abstract LiveData<MetadataEntity> get(long id);

	@Query("SELECT * FROM " + Meta.META + " WHERE " + Meta.URI + " IN (:uris)")
	public abstract LiveData<List<MetadataEntity>> getAll(List<String> uris);

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	public abstract Long insert(MetadataEntity datum);

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	public abstract Long[] insert(MetadataEntity... datums);

	@Update
	public abstract void update(MetadataEntity... datums);

	@Delete
	public abstract void delete(MetadataEntity... datums);

	/**
	 * This is a makeshift cluster until Room supports order clauses
	 * @param andOr
	 * @param nameTime
	 * @param segregate
	 * @param ascDesc
	 * @param labels
	 * @param subjectIds
	 * @param hiddenFolderIds
	 * @param ratings
	 * @return
	 */
	public LiveData<List<GalleryResult>> getImages(
		boolean andOr, boolean nameTime, boolean segregate, boolean ascDesc,
	   	List<String> labels, List<Long> subjectIds, List<Long> hiddenFolderIds, List<Integer> ratings) {
		if (andOr)
			if (segregate)
				if (ascDesc)
					return nameTime ? getImages_AND_SEG_NAME_ASC(labels, subjectIds, hiddenFolderIds, ratings) : getImages_AND_SEG_TIME_ASC(labels, subjectIds, hiddenFolderIds, ratings);
				else
					return nameTime ? getImages_AND_SEG_NAME_DESC(labels, subjectIds, hiddenFolderIds, ratings) : getImages_AND_SEG_TIME_DESC(labels, subjectIds, hiddenFolderIds, ratings);
			else
				if (ascDesc)
					return nameTime ? getImages_AND_NAME_ASC(labels, subjectIds, hiddenFolderIds, ratings) : getImages_AND_TIME_ASC(labels, subjectIds, hiddenFolderIds, ratings);
				else
					return nameTime ? getImages_AND_NAME_DESC(labels, subjectIds, hiddenFolderIds, ratings) : getImages_AND_TIME_DESC(labels, subjectIds, hiddenFolderIds, ratings);
		else
			if (segregate)
				if (ascDesc)
					return nameTime ? getImages_OR_SEG_NAME_ASC(labels, subjectIds, hiddenFolderIds, ratings) : getImages_OR_SEG_TIME_ASC(labels, subjectIds, hiddenFolderIds, ratings);
				else
					return nameTime ? getImages_OR_SEG_NAME_DESC(labels, subjectIds, hiddenFolderIds, ratings) : getImages_OR_SEG_TIME_DESC(labels, subjectIds, hiddenFolderIds, ratings);
			else
				if (ascDesc)
					return nameTime ? getImages_OR_NAME_ASC(labels, subjectIds, hiddenFolderIds, ratings) : getImages_OR_TIME_ASC(labels, subjectIds, hiddenFolderIds, ratings);
				else
					return nameTime ? getImages_OR_NAME_DESC(labels, subjectIds, hiddenFolderIds, ratings) : getImages_OR_TIME_DESC(labels, subjectIds, hiddenFolderIds, ratings);
	}

//	public static String createOrderClause(boolean segregate, XmpFilter.SortColumns sortColumn, boolean ascending)
//	{
//		// TODO: We could technically do this in an annotation
//		String order = ascending ? " ASC" : " DESC";
//		StringBuilder sort = new StringBuilder();
//		if (segregate)
//			sort.append(Meta.TYPE + " COLLATE NOCASE ASC, ");
//
//		switch (sortColumn)
//		{
//			// TODO: This is the data, move the enum here
//			case Date: sort.append(Meta.TIMESTAMP).append(order); break;
//			case Name: sort.append(Meta.NAME).append(" COLLATE NOCASE").append(order); break;
//			default: sort.append(Meta.NAME).append(" COLLATE NOCASE").append(" ASC");
//		}
//		return sort.toString();
//	}
}
