package com.anthonymandra.rawdroid.data

import android.arch.lifecycle.LiveData
import android.arch.paging.DataSource
import android.arch.paging.LivePagedListProvider
import android.arch.persistence.db.SupportSQLiteQuery
import android.arch.persistence.db.SupportSQLiteQueryBuilder
import android.arch.persistence.room.*
import com.anthonymandra.content.Meta
import com.anthonymandra.rawdroid.XmpFilter
import com.anthonymandra.util.DbUtil
import java.util.ArrayList

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

    @RawQuery
    internal abstract fun getImages(query: SupportSQLiteQuery): LiveData<List<MetadataEntity>>

    @RawQuery
    internal abstract fun getImages2(query: SupportSQLiteQuery): DataSource.Factory<Int, MetadataEntity>

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

    public fun getImages(filter: XmpFilter) : DataSource.Factory<Int, MetadataEntity> {
        return getImages2(createFilterQuery(filter))
    }

    private fun createFilterQuery(filter: XmpFilter): SupportSQLiteQuery {
        val selection = StringBuilder()
        val selectionArgs = ArrayList<String>()
        var requiresJoiner = false

        val and = " AND "
        val or = " OR "
        val joiner = if (filter.andTrueOrFalse) and else or

        if (filter.xmp != null) {
            if (filter.xmp.label.isNotEmpty()) {
                requiresJoiner = true

                selection.append(DbUtil.createIN(Meta.LABEL, filter.xmp.label.size))
                selectionArgs.addAll(filter.xmp.label)
            }
            //			if (filter.xmp.subject != null && filter.xmp.subject.length > 0)
            //			{
            //				if (requiresJoiner)
            //					selection.append(joiner);
            //				requiresJoiner = true;
            //
            //				selection.append(DbUtil.createLike(Meta.SUBJECT, filter.xmp.subject,
            //						selectionArgs, joiner, false,
            //						"%", "%",   // openended wildcards, match subject anywhere
            //						null));
            //			}
            if (filter.xmp.rating.isNotEmpty()) {
                if (requiresJoiner)
                    selection.append(joiner)
                requiresJoiner = true

                selection.append(DbUtil.createIN(Meta.RATING, filter.xmp.rating.size))
                filter.xmp.rating.mapTo(selectionArgs) { java.lang.Double.toString(it.toDouble()) }
            }
        }
        if (filter.hiddenFolders.isNotEmpty()) {
            if (requiresJoiner)
                selection.append(and)  // Always exclude the folders, don't OR

            selection.append(DbUtil.createLike(Meta.PARENT,
                    filter.hiddenFolders.toTypedArray(),
                    selectionArgs,
                    and, // Requires AND so multiple hides don't negate each other
                    true, null, // No wild to start, matches path exactly
                    "%", // Wildcard end to match all children
                    "%")// NOT
            )  // Uri contain '%' which means match any so escape them
        }

        val order = if (filter.sortAscending) " ASC" else " DESC"
        val sort = StringBuilder()

        if (filter.segregateByType) {
            sort.append(Meta.TYPE).append(" COLLATE NOCASE").append(" ASC, ")
        }
        when (filter.sortColumn) {
            XmpFilter.SortColumns.Date -> sort.append(Meta.TIMESTAMP).append(order)
            XmpFilter.SortColumns.Name -> sort.append(Meta.NAME).append(" COLLATE NOCASE").append(order)
        }

        //TODO: We need to set the subject selection
        return SupportSQLiteQueryBuilder.builder("meta")
                .selection(selection.toString(), selectionArgs.toArray())
                .orderBy(sort.toString())
                .create()
    }
}
