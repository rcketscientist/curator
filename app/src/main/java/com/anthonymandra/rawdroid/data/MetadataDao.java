package com.anthonymandra.rawdroid.data;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

@Dao
public abstract class MetadataDao {
	// Core query logic, write this in the query initially for annotation error-checking
	private static final String coreQuery =
			"SELECT * FROM meta " +
			"INNER JOIN (SELECT id AS image_parent_id, documentUri AS parentDocument FROM image_parent) image_parent " +
			"ON meta.parentId = image_parent_id " +
			"LEFT JOIN meta_subject_junction " +
			"ON meta.id = meta_subject_junction.metaId " +
			"LEFT JOIN (SELECT id AS xmp_subject_id, name AS keyword FROM xmp_subject) xmp_subject " +
			"ON xmp_subject_id = meta_subject_junction.subjectId ";

	// Core query logic, write this in the query initially for annotation error-checking
	private static final String coreQuery2 =
			"SELECT * FROM meta " +
					"INNER JOIN (SELECT id AS image_parent_id, documentUri AS parentDocument FROM image_parent) image_parent " +
					"ON meta.parentId = image_parent_id " +
					"(SELECT GROUP_CONCAT(name) FROM meta_subject_junction " +
					"JOIN xmp_subject " +
					"ON xmp_subject.id = meta_subject_junction.subjectId " +
					"WHERE meta_subject_junction.metaId = metaId) AS keywords ";

	private static final String mergeQuery =
			"SELECT id, name, type, height, width, orientation,  " +
				"(SELECT GROUP_CONCAT(name) " +
				  "FROM meta_subject_junction " +
				  "JOIN xmp_subject " +
				  "ON xmp_subject.id = meta_subject_junction.subjectId " +
				  "WHERE meta_subject_junction.metaId = meta.id) AS keywords, " +
				"(SELECT documentUri " +
					"FROM image_parent " +
					"WHERE meta.parentId = image_parent.id ) AS parentUri " +
			"FROM meta";

	@Query("SELECT COUNT(*) FROM meta")
	public abstract int count();

	@Query("SELECT uri, id FROM meta")
	public abstract LiveData<List<UriIdResult>> getUriId();

	@Query("SELECT * FROM meta")
	public abstract LiveData<List<MetadataEntity>> getAll();

// --- AND ----
// --- NAME ---

//	@Query(	"SELECT * FROM meta " +
//			"INNER JOIN (SELECT id, documentUri AS parentDocument FROM image_parent) image_parent " +
//			"ON meta.parentId = image_parent.id " +
//			"INNER JOIN meta_subject_junction " +
//			"ON meta.id = meta_subject_junction.metaId " +
//            "LEFT JOIN (SELECT id, name AS keyword FROM xmp_subject) xmp_subject " +
//            "ON xmp_subject.id = meta_subject_junction.subjectId " +
//			"WHERE meta.label IN (:labels) " +
//			"AND meta.rating IN (:ratings) " +
//			"AND meta_subject_junction.subjectId IN (:subjects)" +
//			"AND meta.parentId NOT IN (:hiddenFolderIds)" +
//			"ORDER BY type COLLATE NOCASE ASC, meta.name COLLATE NOCASE ASC")
//	abstract LiveData<List<MetadataWithRelations>> getImages_AND_SEG_NAME_ASC(
//			List<String> labels,
//			List<String> subjects,
//			List<Long> hiddenFolderIds,
//			List<Integer> ratings);

	@Query("SELECT id, name, type, height, width, orientation,  " +
				"(SELECT GROUP_CONCAT(name) " +
					"FROM meta_subject_junction " +
					"JOIN xmp_subject " +
					"ON xmp_subject.id = meta_subject_junction.subjectId " +
					"WHERE meta_subject_junction.metaId = meta.id) AS keywords, " +
				"(SELECT documentUri " +
					"FROM image_parent " +
					"WHERE meta.parentId = image_parent.id ) AS parentUri " +
			"FROM meta")
	abstract LiveData<List<MetadataResult>> getImages();

	@Query("SELECT *,  " +
			"(SELECT GROUP_CONCAT(name) " +
			"FROM meta_subject_junction " +
			"JOIN xmp_subject " +
			"ON xmp_subject.id = meta_subject_junction.subjectId " +
			"WHERE meta_subject_junction.metaId = meta.id) AS keywords, " +
			"(SELECT documentUri " +
			"FROM image_parent " +
			"WHERE meta.parentId = image_parent.id ) AS parentUri " +
			"FROM meta")
	abstract LiveData<List<MetadataResult>> getImages2();

	@Query(	coreQuery +
			"WHERE meta.label IN (:labels) " +
			"AND meta.rating IN (:ratings) " +
			"AND meta_subject_junction.subjectId IN (:subjects)" +
			"AND meta.parentId NOT IN (:hiddenFolderIds)" +
			"ORDER BY type COLLATE NOCASE ASC, meta.name COLLATE NOCASE ASC")
	abstract LiveData<List<MetadataEntity>> getImages_AND_SEG_NAME_ASC(
			List<String> labels,
			List<String> subjects,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parentId = image_parent.id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.id = meta_subject_junction.metaId " +
            "LEFT JOIN xmp_subject " +
            "ON xmp_subject.id = meta_subject_junction.subjectId " +
			"WHERE meta.label IN (:labels) " +
			"AND meta.rating IN (:ratings) " +
			"AND meta_subject_junction.subjectId IN (:subjects)" +
			"AND meta.parentId NOT IN (:hiddenFolderIds)" +
			"ORDER BY type COLLATE NOCASE ASC, meta.name COLLATE NOCASE DESC")
	abstract LiveData<List<MetadataEntity>> getImages_AND_SEG_NAME_DESC(
			List<String> labels,
			List<String> subjects,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parentId = image_parent.id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.id = meta_subject_junction.metaId " +
            "LEFT JOIN xmp_subject " +
            "ON xmp_subject.id = meta_subject_junction.subjectId " +
			"WHERE meta.label IN (:labels) " +
			"AND meta.rating IN (:ratings) " +
			"AND meta_subject_junction.subjectId IN (:subjects)" +
			"AND meta.parentId NOT IN (:hiddenFolderIds)" +
			"ORDER BY meta.name COLLATE NOCASE ASC")
	abstract LiveData<List<MetadataEntity>> getImages_AND_NAME_ASC(
			List<String> labels,
			List<String> subjects,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parentId = image_parent.id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.id = meta_subject_junction.metaId " +
            "LEFT JOIN xmp_subject " +
            "ON xmp_subject.id = meta_subject_junction.subjectId " +
			"WHERE meta.label IN (:labels) " +
			"AND meta.rating IN (:ratings) " +
			"AND meta_subject_junction.subjectId IN (:subjects)" +
			"AND meta.parentId NOT IN (:hiddenFolderIds)" +
			"ORDER BY meta.name COLLATE NOCASE DESC")
	abstract LiveData<List<MetadataEntity>> getImages_AND_NAME_DESC(
			List<String> labels,
			List<String> subjects,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

//---- TIME ----

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parentId = image_parent.id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.id = meta_subject_junction.metaId " +
            "LEFT JOIN xmp_subject " +
            "ON xmp_subject.id = meta_subject_junction.subjectId " +
			"WHERE meta.label IN (:labels) " +
			"AND meta.rating IN (:ratings) " +
			"AND meta_subject_junction.subjectId IN (:subjects)" +
			"AND meta.parentId NOT IN (:hiddenFolderIds)" +
			"ORDER BY type COLLATE NOCASE ASC, meta.timestamp COLLATE NOCASE ASC")
	abstract LiveData<List<MetadataEntity>> getImages_AND_SEG_TIME_ASC(
			List<String> labels,
			List<String> subjects,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parentId = image_parent.id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.id = meta_subject_junction.metaId " +
            "LEFT JOIN xmp_subject " +
            "ON xmp_subject.id = meta_subject_junction.subjectId " +
			"WHERE meta.label IN (:labels) " +
			"AND meta.rating IN (:ratings) " +
			"AND meta_subject_junction.subjectId IN (:subjects)" +
			"AND meta.parentId NOT IN (:hiddenFolderIds)" +
			"ORDER BY type COLLATE NOCASE ASC, meta.timestamp COLLATE NOCASE DESC")
	abstract LiveData<List<MetadataEntity>> getImages_AND_SEG_TIME_DESC(
			List<String> labels,
			List<String> subjects,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parentId = image_parent.id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.id = meta_subject_junction.metaId " +
            "LEFT JOIN xmp_subject " +
            "ON xmp_subject.id = meta_subject_junction.subjectId " +
			"WHERE meta.label IN (:labels) " +
			"AND meta.rating IN (:ratings) " +
			"AND meta_subject_junction.subjectId IN (:subjects)" +
			"AND meta.parentId NOT IN (:hiddenFolderIds)" +
			"ORDER BY meta.timestamp COLLATE NOCASE ASC")
	abstract LiveData<List<MetadataEntity>> getImages_AND_TIME_ASC(
			List<String> labels,
			List<String> subjects,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parentId = image_parent.id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.id = meta_subject_junction.metaId " +
            "LEFT JOIN xmp_subject " +
            "ON xmp_subject.id = meta_subject_junction.subjectId " +
			"WHERE meta.label IN (:labels) " +
			"AND meta.rating IN (:ratings) " +
			"AND meta_subject_junction.subjectId IN (:subjects)" +
			"AND meta.parentId NOT IN (:hiddenFolderIds)" +
			"ORDER BY meta.timestamp COLLATE NOCASE DESC")
	abstract LiveData<List<MetadataEntity>> getImages_AND_TIME_DESC(
			List<String> labels,
			List<String> subjects,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

//--- OR ----
//--- NAME ---

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parentId = image_parent.id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.id = meta_subject_junction.metaId " +
            "LEFT JOIN xmp_subject " +
            "ON xmp_subject.id = meta_subject_junction.subjectId " +
			"WHERE meta.label IN (:labels) " +
			"OR meta.rating IN (:ratings) " +
			"OR meta_subject_junction.subjectId IN (:subjects)" +
			"AND meta.parentId NOT IN (:hiddenFolderIds)" +	// Always exclude folders
			"ORDER BY type COLLATE NOCASE ASC, meta.name COLLATE NOCASE ASC")
	abstract LiveData<List<MetadataEntity>> getImages_OR_SEG_NAME_ASC(
			List<String> labels,
			List<String> subjects,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parentId = image_parent.id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.id = meta_subject_junction.metaId " +
            "LEFT JOIN xmp_subject " +
            "ON xmp_subject.id = meta_subject_junction.subjectId " +
			"WHERE meta.label IN (:labels) " +
			"OR meta.rating IN (:ratings) " +
			"OR meta_subject_junction.subjectId IN (:subjects)" +
			"AND meta.parentId NOT IN (:hiddenFolderIds)" +	// Always exclude folders
			"ORDER BY type COLLATE NOCASE ASC, meta.name COLLATE NOCASE DESC")
	abstract LiveData<List<MetadataEntity>> getImages_OR_SEG_NAME_DESC(
			List<String> labels,
			List<String> subjects,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parentId = image_parent.id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.id = meta_subject_junction.metaId " +
            "LEFT JOIN xmp_subject " +
            "ON xmp_subject.id = meta_subject_junction.subjectId " +
			"WHERE meta.label IN (:labels) " +
			"OR meta.rating IN (:ratings) " +
			"OR meta_subject_junction.subjectId IN (:subjects)" +
			"AND meta.parentId NOT IN (:hiddenFolderIds)" +	// Always exclude folders
			"ORDER BY meta.name COLLATE NOCASE ASC")
	abstract LiveData<List<MetadataEntity>> getImages_OR_NAME_ASC(
			List<String> labels,
			List<String> subjects,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parentId = image_parent.id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.id = meta_subject_junction.metaId " +
            "LEFT JOIN xmp_subject " +
            "ON xmp_subject.id = meta_subject_junction.subjectId " +
			"WHERE meta.label IN (:labels) " +
			"OR meta.rating IN (:ratings) " +
			"OR meta_subject_junction.subjectId IN (:subjects)" +
			"AND meta.parentId NOT IN (:hiddenFolderIds)" +	// Always exclude folders
			"ORDER BY meta.name COLLATE NOCASE DESC")
	abstract LiveData<List<MetadataEntity>> getImages_OR_NAME_DESC(
			List<String> labels,
			List<String> subjects,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

//--- TIME ---

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parentId = image_parent.id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.id = meta_subject_junction.metaId " +
            "LEFT JOIN xmp_subject " +
            "ON xmp_subject.id = meta_subject_junction.subjectId " +
			"WHERE meta.label IN (:labels) " +
			"OR meta.rating IN (:ratings) " +
			"OR meta_subject_junction.subjectId IN (:subjects)" +
			"AND meta.parentId NOT IN (:hiddenFolderIds)" +	// Always exclude folders
			"ORDER BY type COLLATE NOCASE ASC, meta.timestamp COLLATE NOCASE ASC")
	abstract LiveData<List<MetadataEntity>> getImages_OR_SEG_TIME_ASC(
			List<String> labels,
			List<String> subjects,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parentId = image_parent.id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.id = meta_subject_junction.metaId " +
            "LEFT JOIN xmp_subject " +
            "ON xmp_subject.id = meta_subject_junction.subjectId " +
			"WHERE meta.label IN (:labels) " +
			"OR meta.rating IN (:ratings) " +
			"OR meta_subject_junction.subjectId IN (:subjects)" +
			"AND meta.parentId NOT IN (:hiddenFolderIds)" +	// Always exclude folders
			"ORDER BY type COLLATE NOCASE ASC, meta.timestamp COLLATE NOCASE DESC")
	abstract LiveData<List<MetadataEntity>> getImages_OR_SEG_TIME_DESC(
			List<String> labels,
			List<String> subjects,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parentId = image_parent.id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.id = meta_subject_junction.metaId " +
            "LEFT JOIN xmp_subject " +
            "ON xmp_subject.id = meta_subject_junction.subjectId " +
			"WHERE meta.label IN (:labels) " +
			"OR meta.rating IN (:ratings) " +
			"OR meta_subject_junction.subjectId IN (:subjects)" +
			"AND meta.parentId NOT IN (:hiddenFolderIds)" +	// Always exclude folders
			"ORDER BY meta.timestamp COLLATE NOCASE ASC")
	abstract LiveData<List<MetadataEntity>> getImages_OR_TIME_ASC(
			List<String> labels,
			List<String> subjects,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query(	"SELECT * FROM meta " +
			"INNER JOIN image_parent " +
			"ON meta.parentId = image_parent.id " +
			"INNER JOIN meta_subject_junction " +
			"ON meta.id = meta_subject_junction.metaId " +
            "LEFT JOIN xmp_subject " +
            "ON xmp_subject.id = meta_subject_junction.subjectId " +
			"WHERE meta.label IN (:labels) " +
			"OR meta.rating IN (:ratings) " +
			"OR meta_subject_junction.subjectId IN (:subjects)" +
			"AND meta.parentId NOT IN (:hiddenFolderIds)" +	// Always exclude folders
			"ORDER BY meta.timestamp COLLATE NOCASE DESC")
	abstract LiveData<List<MetadataEntity>> getImages_OR_TIME_DESC(
			List<String> labels,
			List<String> subjects,
			List<Long> hiddenFolderIds,
			List<Integer> ratings);

	@Query("SELECT * FROM meta WHERE id = :id")
	public abstract LiveData<MetadataEntity> get(long id);

	@Query("SELECT * FROM meta WHERE uri IN (:uris)")
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
	 * @param subjects
	 * @param hiddenFolderIds
	 * @param ratings
	 * @return
	 */
	public LiveData<List<MetadataEntity>> getImages(
		boolean andOr, boolean nameTime, boolean segregate, boolean ascDesc,
	   	List<String> labels, List<String> subjects, List<Long> hiddenFolderIds, List<Integer> ratings) {
		if (andOr)
			if (segregate)
				if (ascDesc)
					return nameTime ? getImages_AND_SEG_NAME_ASC(labels, subjects, hiddenFolderIds, ratings) : getImages_AND_SEG_TIME_ASC(labels, subjects, hiddenFolderIds, ratings);
				else
					return nameTime ? getImages_AND_SEG_NAME_DESC(labels, subjects, hiddenFolderIds, ratings) : getImages_AND_SEG_TIME_DESC(labels, subjects, hiddenFolderIds, ratings);
			else
				if (ascDesc)
					return nameTime ? getImages_AND_NAME_ASC(labels, subjects, hiddenFolderIds, ratings) : getImages_AND_TIME_ASC(labels, subjects, hiddenFolderIds, ratings);
				else
					return nameTime ? getImages_AND_NAME_DESC(labels, subjects, hiddenFolderIds, ratings) : getImages_AND_TIME_DESC(labels, subjects, hiddenFolderIds, ratings);
		else
			if (segregate)
				if (ascDesc)
					return nameTime ? getImages_OR_SEG_NAME_ASC(labels, subjects, hiddenFolderIds, ratings) : getImages_OR_SEG_TIME_ASC(labels, subjects, hiddenFolderIds, ratings);
				else
					return nameTime ? getImages_OR_SEG_NAME_DESC(labels, subjects, hiddenFolderIds, ratings) : getImages_OR_SEG_TIME_DESC(labels, subjects, hiddenFolderIds, ratings);
			else
				if (ascDesc)
					return nameTime ? getImages_OR_NAME_ASC(labels, subjects, hiddenFolderIds, ratings) : getImages_OR_TIME_ASC(labels, subjects, hiddenFolderIds, ratings);
				else
					return nameTime ? getImages_OR_NAME_DESC(labels, subjects, hiddenFolderIds, ratings) : getImages_OR_TIME_DESC(labels, subjects, hiddenFolderIds, ratings);
	}
}
