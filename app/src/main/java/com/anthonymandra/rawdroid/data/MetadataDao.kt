package com.anthonymandra.rawdroid.data

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.Dao
import android.arch.persistence.room.Delete
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Query
import android.arch.persistence.room.Update

@Dao
abstract class MetadataDao {

    @get:Query("SELECT uri, id FROM meta")
    abstract val uriId: LiveData<List<UriIdResult>>

    @get:Query("SELECT * FROM meta")
    abstract val all: LiveData<List<MetadataEntity>>

    // --- AND ----
    // --- NAME ---

    @get:Query(mergeQuery)
    internal abstract val images: LiveData<List<MetadataResult>>

    @get:Query(mergeQuery + "WHERE label IN (\"label1\")")
    internal abstract val imagesTest: LiveData<List<MetadataResult>>

    @Query("SELECT COUNT(*) FROM meta")
    abstract fun count(): Int

    @Query("SELECT * FROM meta " +
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
        OrderTypeNameAsc)
    internal abstract fun getImages_AND_SEG_NAME_ASC(
        labels: List<String>,
        subjects: List<String>,
        hiddenFolderIds: List<Long>,
        ratings: List<Int>): LiveData<List<MetadataEntity>>

    @Query("SELECT * FROM meta " +
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
    internal abstract fun getImages_AND_SEG_NAME_DESC(
        labels: List<String>,
        subjects: List<String>,
        hiddenFolderIds: List<Long>,
        ratings: List<Int>): LiveData<List<MetadataEntity>>

    @Query("SELECT * FROM meta " +
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
    internal abstract fun getImages_AND_NAME_ASC(
        labels: List<String>,
        subjects: List<String>,
        hiddenFolderIds: List<Long>,
        ratings: List<Int>): LiveData<List<MetadataEntity>>

    @Query("SELECT * FROM meta " +
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
    internal abstract fun getImages_AND_NAME_DESC(
        labels: List<String>,
        subjects: List<String>,
        hiddenFolderIds: List<Long>,
        ratings: List<Int>): LiveData<List<MetadataEntity>>

    //---- TIME ----

    @Query("SELECT * FROM meta " +
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
    internal abstract fun getImages_AND_SEG_TIME_ASC(
        labels: List<String>,
        subjects: List<String>,
        hiddenFolderIds: List<Long>,
        ratings: List<Int>): LiveData<List<MetadataEntity>>

    @Query("SELECT * FROM meta " +
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
    internal abstract fun getImages_AND_SEG_TIME_DESC(
        labels: List<String>,
        subjects: List<String>,
        hiddenFolderIds: List<Long>,
        ratings: List<Int>): LiveData<List<MetadataEntity>>

    @Query("SELECT * FROM meta " +
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
    internal abstract fun getImages_AND_TIME_ASC(
        labels: List<String>,
        subjects: List<String>,
        hiddenFolderIds: List<Long>,
        ratings: List<Int>): LiveData<List<MetadataEntity>>

    @Query("SELECT * FROM meta " +
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
    internal abstract fun getImages_AND_TIME_DESC(
        labels: List<String>,
        subjects: List<String>,
        hiddenFolderIds: List<Long>,
        ratings: List<Int>): LiveData<List<MetadataEntity>>

    //--- OR ----
    //--- NAME ---

    @Query("SELECT * FROM meta " +
        "INNER JOIN image_parent " +
        "ON meta.parentId = image_parent.id " +
        "INNER JOIN meta_subject_junction " +
        "ON meta.id = meta_subject_junction.metaId " +
        "LEFT JOIN xmp_subject " +
        "ON xmp_subject.id = meta_subject_junction.subjectId " +
        "WHERE meta.label IN (:labels) " +
        "OR meta.rating IN (:ratings) " +
        "OR meta_subject_junction.subjectId IN (:subjects)" +
        "AND meta.parentId NOT IN (:hiddenFolderIds)" +    // Always exclude folders

        "ORDER BY type COLLATE NOCASE ASC, meta.name COLLATE NOCASE ASC")
    internal abstract fun getImages_OR_SEG_NAME_ASC(
        labels: List<String>,
        subjects: List<String>,
        hiddenFolderIds: List<Long>,
        ratings: List<Int>): LiveData<List<MetadataEntity>>

    @Query("SELECT * FROM meta " +
        "INNER JOIN image_parent " +
        "ON meta.parentId = image_parent.id " +
        "INNER JOIN meta_subject_junction " +
        "ON meta.id = meta_subject_junction.metaId " +
        "LEFT JOIN xmp_subject " +
        "ON xmp_subject.id = meta_subject_junction.subjectId " +
        "WHERE meta.label IN (:labels) " +
        "OR meta.rating IN (:ratings) " +
        "OR meta_subject_junction.subjectId IN (:subjects)" +
        "AND meta.parentId NOT IN (:hiddenFolderIds)" +    // Always exclude folders

        "ORDER BY type COLLATE NOCASE ASC, meta.name COLLATE NOCASE DESC")
    internal abstract fun getImages_OR_SEG_NAME_DESC(
        labels: List<String>,
        subjects: List<String>,
        hiddenFolderIds: List<Long>,
        ratings: List<Int>): LiveData<List<MetadataEntity>>

    @Query("SELECT * FROM meta " +
        "INNER JOIN image_parent " +
        "ON meta.parentId = image_parent.id " +
        "INNER JOIN meta_subject_junction " +
        "ON meta.id = meta_subject_junction.metaId " +
        "LEFT JOIN xmp_subject " +
        "ON xmp_subject.id = meta_subject_junction.subjectId " +
        "WHERE meta.label IN (:labels) " +
        "OR meta.rating IN (:ratings) " +
        "OR meta_subject_junction.subjectId IN (:subjects)" +
        "AND meta.parentId NOT IN (:hiddenFolderIds)" +    // Always exclude folders

        "ORDER BY meta.name COLLATE NOCASE ASC")
    internal abstract fun getImages_OR_NAME_ASC(
        labels: List<String>,
        subjects: List<String>,
        hiddenFolderIds: List<Long>,
        ratings: List<Int>): LiveData<List<MetadataEntity>>

    @Query("SELECT * FROM meta " +
        "INNER JOIN image_parent " +
        "ON meta.parentId = image_parent.id " +
        "INNER JOIN meta_subject_junction " +
        "ON meta.id = meta_subject_junction.metaId " +
        "LEFT JOIN xmp_subject " +
        "ON xmp_subject.id = meta_subject_junction.subjectId " +
        "WHERE meta.label IN (:labels) " +
        "OR meta.rating IN (:ratings) " +
        "OR meta_subject_junction.subjectId IN (:subjects)" +
        "AND meta.parentId NOT IN (:hiddenFolderIds)" +    // Always exclude folders

        "ORDER BY meta.name COLLATE NOCASE DESC")
    internal abstract fun getImages_OR_NAME_DESC(
        labels: List<String>,
        subjects: List<String>,
        hiddenFolderIds: List<Long>,
        ratings: List<Int>): LiveData<List<MetadataEntity>>

    //--- TIME ---

    @Query("SELECT * FROM meta " +
        "INNER JOIN image_parent " +
        "ON meta.parentId = image_parent.id " +
        "INNER JOIN meta_subject_junction " +
        "ON meta.id = meta_subject_junction.metaId " +
        "LEFT JOIN xmp_subject " +
        "ON xmp_subject.id = meta_subject_junction.subjectId " +
        "WHERE meta.label IN (:labels) " +
        "OR meta.rating IN (:ratings) " +
        "OR meta_subject_junction.subjectId IN (:subjects)" +
        "AND meta.parentId NOT IN (:hiddenFolderIds)" +    // Always exclude folders

        "ORDER BY type COLLATE NOCASE ASC, meta.timestamp COLLATE NOCASE ASC")
    internal abstract fun getImages_OR_SEG_TIME_ASC(
        labels: List<String>,
        subjects: List<String>,
        hiddenFolderIds: List<Long>,
        ratings: List<Int>): LiveData<List<MetadataEntity>>

    @Query("SELECT * FROM meta " +
        "INNER JOIN image_parent " +
        "ON meta.parentId = image_parent.id " +
        "INNER JOIN meta_subject_junction " +
        "ON meta.id = meta_subject_junction.metaId " +
        "LEFT JOIN xmp_subject " +
        "ON xmp_subject.id = meta_subject_junction.subjectId " +
        "WHERE meta.label IN (:labels) " +
        "OR meta.rating IN (:ratings) " +
        "OR meta_subject_junction.subjectId IN (:subjects)" +
        "AND meta.parentId NOT IN (:hiddenFolderIds)" +    // Always exclude folders

        "ORDER BY type COLLATE NOCASE ASC, meta.timestamp COLLATE NOCASE DESC")
    internal abstract fun getImages_OR_SEG_TIME_DESC(
        labels: List<String>,
        subjects: List<String>,
        hiddenFolderIds: List<Long>,
        ratings: List<Int>): LiveData<List<MetadataEntity>>

    @Query("SELECT * FROM meta " +
        "INNER JOIN image_parent " +
        "ON meta.parentId = image_parent.id " +
        "INNER JOIN meta_subject_junction " +
        "ON meta.id = meta_subject_junction.metaId " +
        "LEFT JOIN xmp_subject " +
        "ON xmp_subject.id = meta_subject_junction.subjectId " +
        "WHERE meta.label IN (:labels) " +
        "OR meta.rating IN (:ratings) " +
        "OR meta_subject_junction.subjectId IN (:subjects)" +
        "AND meta.parentId NOT IN (:hiddenFolderIds)" +    // Always exclude folders

        "ORDER BY meta.timestamp COLLATE NOCASE ASC")
    internal abstract fun getImages_OR_TIME_ASC(
        labels: List<String>,
        subjects: List<String>,
        hiddenFolderIds: List<Long>,
        ratings: List<Int>): LiveData<List<MetadataEntity>>

    @Query("SELECT * FROM meta " +
        "INNER JOIN image_parent " +
        "ON meta.parentId = image_parent.id " +
        "INNER JOIN meta_subject_junction " +
        "ON meta.id = meta_subject_junction.metaId " +
        "LEFT JOIN xmp_subject " +
        "ON xmp_subject.id = meta_subject_junction.subjectId " +
        "WHERE meta.label IN (:labels) " +
        "OR meta.rating IN (:ratings) " +
        "OR meta_subject_junction.subjectId IN (:subjects)" +
        "AND meta.parentId NOT IN (:hiddenFolderIds)" +    // Always exclude folders

        "ORDER BY meta.timestamp COLLATE NOCASE DESC")
    internal abstract fun getImages_OR_TIME_DESC(
        labels: List<String>,
        subjects: List<String>,
        hiddenFolderIds: List<Long>,
        ratings: List<Int>): LiveData<List<MetadataEntity>>

    @Query("SELECT * FROM meta WHERE id = :id")
    abstract operator fun get(id: Long): LiveData<MetadataEntity>

    @Query("SELECT * FROM meta WHERE uri IN (:uris)")
    abstract fun getAll(uris: List<String>): LiveData<List<MetadataEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(datum: MetadataEntity): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(vararg datums: MetadataEntity): Array<Long>

    @Update
    abstract fun update(vararg datums: MetadataEntity)

    @Delete
    abstract fun delete(vararg datums: MetadataEntity)

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
    fun getImages(
        andOr: Boolean, nameTime: Boolean, segregate: Boolean, ascDesc: Boolean,
        labels: List<String>, subjects: List<String>, hiddenFolderIds: List<Long>, ratings: List<Int>): LiveData<List<MetadataEntity>> {
        return if (andOr)
            if (segregate)
                if (ascDesc)
                    if (nameTime) getImages_AND_SEG_NAME_ASC(labels, subjects, hiddenFolderIds, ratings) else getImages_AND_SEG_TIME_ASC(labels, subjects, hiddenFolderIds, ratings)
                else
                    if (nameTime) getImages_AND_SEG_NAME_DESC(labels, subjects, hiddenFolderIds, ratings) else getImages_AND_SEG_TIME_DESC(labels, subjects, hiddenFolderIds, ratings)
            else if (ascDesc)
                if (nameTime) getImages_AND_NAME_ASC(labels, subjects, hiddenFolderIds, ratings) else getImages_AND_TIME_ASC(labels, subjects, hiddenFolderIds, ratings)
            else
                if (nameTime) getImages_AND_NAME_DESC(labels, subjects, hiddenFolderIds, ratings) else getImages_AND_TIME_DESC(labels, subjects, hiddenFolderIds, ratings)
        else if (segregate)
            if (ascDesc)
                if (nameTime) getImages_OR_SEG_NAME_ASC(labels, subjects, hiddenFolderIds, ratings) else getImages_OR_SEG_TIME_ASC(labels, subjects, hiddenFolderIds, ratings)
            else
                if (nameTime) getImages_OR_SEG_NAME_DESC(labels, subjects, hiddenFolderIds, ratings) else getImages_OR_SEG_TIME_DESC(labels, subjects, hiddenFolderIds, ratings)
        else if (ascDesc)
            if (nameTime) getImages_OR_NAME_ASC(labels, subjects, hiddenFolderIds, ratings) else getImages_OR_TIME_ASC(labels, subjects, hiddenFolderIds, ratings)
        else
            if (nameTime) getImages_OR_NAME_DESC(labels, subjects, hiddenFolderIds, ratings) else getImages_OR_TIME_DESC(labels, subjects, hiddenFolderIds, ratings)
    }

    companion object {
        // Core query logic, write this in the query initially for annotation error-checking
        private const val mergeQuery = "SELECT *,  " +
            "(SELECT GROUP_CONCAT(name) " +
            "FROM meta_subject_junction " +
            "JOIN xmp_subject " +
            "ON xmp_subject.id = meta_subject_junction.subjectId " +
            "WHERE meta_subject_junction.metaId = meta.id) AS keywords, " +
            "(SELECT documentUri " +
            "FROM image_parent " +
            "WHERE meta.parentId = image_parent.id ) AS parentUri " +
            "FROM meta "

        private const val SEGREGATE = "type COLLATE NOCASE ASC"
        private const val NAME_ASC = "meta.name COLLATE NOCASE ASC"
        private const val OrderTypeNameAsc = "ORDER BY type COLLATE NOCASE ASC, meta.name COLLATE NOCASE ASC"
    }
}
