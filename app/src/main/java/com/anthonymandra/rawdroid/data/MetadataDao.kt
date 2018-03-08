package com.anthonymandra.rawdroid.data

import android.arch.lifecycle.LiveData
import android.arch.persistence.db.SimpleSQLiteQuery
import android.arch.persistence.db.SupportSQLiteQuery
import android.arch.persistence.room.*
import com.anthonymandra.content.Meta
import com.anthonymandra.rawdroid.XmpFilter
import com.anthonymandra.util.DbUtil
import java.util.*

@Dao
abstract class MetadataDao {

    @get:Query("SELECT uri, id FROM meta")
    abstract val uriId: LiveData<List<UriIdResult>>

    @get:Query("SELECT * FROM meta")
    abstract val allImages: LiveData<List<MetadataEntity>>

    val allMetadata: LiveData<List<MetadataResult>> = getImages()

    @Query(
        "SELECT meta.*, " +
            "(SELECT GROUP_CONCAT(name) " +
                "FROM meta_subject_junction " +
                "JOIN xmp_subject " +
                "ON xmp_subject.id = meta_subject_junction.subjectId " +
                "WHERE meta_subject_junction.metaId = meta.id) AS keywords, " +
            "(SELECT documentUri " +
                "FROM image_parent " +
                "WHERE meta.parentId = image_parent.id ) AS parentUri, " +
            "meta_subject_junction.subjectId " +
            "FROM meta " +
            "LEFT JOIN meta_subject_junction ON meta_subject_junction.metaId = meta.id " +
            "WHERE subjectId IN (2,3)" + // .format("column IN (?,?)
            "GROUP BY meta.id")
    abstract fun test(): LiveData<List<MetadataResult>>

    @Query(
    "SELECT meta.* " +
        " FROM meta " +
        " LEFT JOIN meta_subject_junction ON meta_subject_junction.metaId = meta.id" +
        " WHERE subjectId IN (1,3)")
    abstract fun test2(): LiveData<List<MetadataTest>>

    @Query("SELECT COUNT(*) FROM meta")
    abstract fun count(): Int

    @RawQuery(observedEntities = [ MetadataEntity::class, FolderEntity::class, SubjectJunction::class ])
    internal abstract fun internalGetImages(query: SupportSQLiteQuery): LiveData<List<MetadataResult>>

    @RawQuery(observedEntities = [ MetadataEntity::class, FolderEntity::class, SubjectJunction::class ])
    internal abstract fun internalGetRelationImages(query: SupportSQLiteQuery): LiveData<List<MetadataTest>>

//    @RawQuery(observedEntities = [ MetadataEntity::class, FolderEntity::class, SubjectJunction::class ])
//    internal abstract fun internalGetImages(query: SupportSQLiteQuery): DataSource.Factory<Int, MetadataEntity>

    @Query("SELECT * FROM meta WHERE id = :id")
    abstract operator fun get(id: Long): LiveData<MetadataEntity>

    @Query("SELECT * FROM meta WHERE uri IN (:uris)")
    abstract fun getAll(uris: List<String>): LiveData<List<MetadataEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    internal abstract fun insert(datum: MetadataEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    internal abstract fun insert(vararg datums: MetadataEntity): List<Long>

    @Update
    abstract fun update(vararg datums: MetadataEntity)

    @Delete
    abstract fun delete(vararg datums: MetadataEntity)

    companion object {
        /**
         * If using a relation for subject this is a far simpler solution.
         */
        private const val relationQuery =
            "SELECT *" +
            " FROM meta" +
            " LEFT JOIN meta_subject_junction ON meta_subject_junction.metaId = meta.id"

        // Core query logic, write this in the query initially for annotation error-checking
        private const val mergeQuery =
            "SELECT *,  " +
            "(SELECT GROUP_CONCAT(name) " +
                "FROM meta_subject_junction " +
                "JOIN xmp_subject " +
                "ON xmp_subject.id = meta_subject_junction.subjectId " +
                "WHERE meta_subject_junction.metaId = meta.id) AS keywords, " +
            "(SELECT documentUri " +
                "FROM image_parent " +
                "WHERE meta.parentId = image_parent.id ) AS parentUri " +
            "FROM meta "

        /**
         * Basic merge and select to be used when there isn't a subject filter.
         * Subject is a special case/merge due to M:N
         */
        private const val mergeSelect =
            "SELECT *" +
            ", (SELECT GROUP_CONCAT(id)" +
                " FROM meta_subject_junction" +
                " JOIN xmp_subject ON xmp_subject.id = meta_subject_junction.subjectId" +
                " WHERE meta_subject_junction.metaId = meta.id) AS keywords" +
            " FROM meta "

        // TODO: Do we even need the parent as string?
        /**
         * Only use when a subject filter exists!
         * This merge keeps concats the full subject list and retains individual subjectId
         * result rows.  This allows images to be filtered by subject and retain the full
         * subject list.
         */
        private const val mergeSelectWithSubject =
            "SELECT meta.*" +
            ", (SELECT GROUP_CONCAT(id)" +
                " FROM meta_subject_junction" +
                " JOIN xmp_subject ON xmp_subject.id = meta_subject_junction.subjectId" +
                " WHERE meta_subject_junction.metaId = meta.id) AS keywords" +
            ", meta_subject_junction.subjectId" +
            " FROM meta" +
            " LEFT JOIN meta_subject_junction ON meta_subject_junction.metaId = meta.id" +
            " WHERE %s" // .format("column IN (?,?), ie: subjectId IN (2,3)
//            " GROUP BY meta.id"

        private const val fromQuery = " FROM meta"
        private const val SEGREGATE = "type COLLATE NOCASE ASC"
        private const val NAME_ASC = "meta.name COLLATE NOCASE ASC"
        private const val OrderTypeNameAsc = "ORDER BY type COLLATE NOCASE ASC, meta.name COLLATE NOCASE ASC"
    }

//    public fun getImages(filter: XmpFilter) : DataSource.Factory<Int, MetadataEntity> {
//        return internalGetImages(createFilterQuery(filter))
//    }

    /**
     * Get image set filterd and ordered by @param filter
     */
    fun getImages(filter: XmpFilter) : LiveData<List<MetadataResult>> {
        return internalGetImages(createFilterQuery(filter))
    }

    fun getRelationImages(filter: XmpFilter) : LiveData<List<MetadataTest>> {
        return internalGetRelationImages(createRelationQuery(filter))
    }

    /**
     * get with default filter will return all with default sorting
     */
    private fun getImages() : LiveData<List<MetadataResult>> {
        return getImages(XmpFilter())
    }

    private fun createRelationQuery(filter: XmpFilter): SupportSQLiteQuery {
        val query = StringBuilder()
        val selection = StringBuilder()
        val order = StringBuilder()
        val selectionArgs = ArrayList<Any>()

        val and = " AND "
        val or = " OR "
        val joiner = if (filter.andTrueOrFalse) and else or

        if (filter.xmp != null) {
            // Special case, append to join query
            if (filter.xmp.subject.isNotEmpty()) {
                if (!selection.isEmpty())
                    selection.append(joiner)

                selection.append(DbUtil.createIN("subjectId", filter.xmp.subject.size))
                filter.xmp.subject.mapTo(selectionArgs) { it.id }
            }

            if (filter.xmp.label.isNotEmpty()) {
                if (!selection.isEmpty())
                    selection.append(joiner)

                selection.append(DbUtil.createIN(Meta.LABEL, filter.xmp.label.size))
                selectionArgs.addAll(filter.xmp.label)
            }

            if (filter.xmp.rating.isNotEmpty()) {
                if (!selection.isEmpty())
                    selection.append(joiner)

                selection.append(DbUtil.createIN(Meta.RATING, filter.xmp.rating.size))
                filter.xmp.rating.mapTo(selectionArgs) { java.lang.Double.toString(it.toDouble()) }
            }
        }

        if (filter.hiddenFolders.isNotEmpty()) {
            if (!selection.isEmpty())
                selection.append(and)       // Always exclude the folders, don't OR
            selection.append(" NOT " )      // Not in hidden folders

            selection.append(
                    DbUtil.createIN("parentId", filter.hiddenFolders)) // FIXME: Should be Long
        }

        order.append(" ORDER BY ")
        val direction = if (filter.sortAscending) " ASC" else " DESC"

        if (filter.segregateByType) {
            order.append(Meta.TYPE).append(" COLLATE NOCASE").append(" ASC, ")
        }
        when (filter.sortColumn) {
            XmpFilter.SortColumns.Date -> order.append(Meta.TIMESTAMP).append(direction)
            XmpFilter.SortColumns.Name -> order.append(Meta.NAME).append(" COLLATE NOCASE").append(direction)
        }

        query.append(relationQuery)
        if (selection.isNotEmpty()) {
            query.append(" WHERE ")
            query.append(selection)
        }
        query.append(order)

        return SimpleSQLiteQuery(query.toString(), selectionArgs.toArray())
    }

    private fun createFilterQuery(filter: XmpFilter): SupportSQLiteQuery {
        val query = StringBuilder()
        val selection = StringBuilder()
        val group = StringBuilder()
        val order = StringBuilder()
        val selectionArgs = ArrayList<Any>()

        val and = " AND "
        val or = " OR "
        val joiner = if (filter.andTrueOrFalse) and else or
        var coreQuery = mergeSelect

        if (filter.xmp != null) {
            // Special case, append to join query
            if (filter.xmp.subject.isNotEmpty()) {
                coreQuery = mergeSelectWithSubject.format(
                        DbUtil.createIN("subjectId", filter.xmp.subject.map { it.id }))

                group.append(" GROUP BY meta.id")
            }

            if (filter.xmp.label.isNotEmpty()) {
                if (!selection.isEmpty())
                    selection.append(joiner)

                selection.append(DbUtil.createIN(Meta.LABEL, filter.xmp.label.size))
                selectionArgs.addAll(filter.xmp.label)
            }

            if (filter.xmp.rating.isNotEmpty()) {
                if (!selection.isEmpty())
                    selection.append(joiner)

                selection.append(DbUtil.createIN(Meta.RATING, filter.xmp.rating.size))
                filter.xmp.rating.mapTo(selectionArgs) { java.lang.Double.toString(it.toDouble()) }
            }
        }

        if (filter.hiddenFolders.isNotEmpty()) {
            if (!selection.isEmpty())
                selection.append(and)       // Always exclude the folders, don't OR
            selection.append(" NOT " )      // Not in hidden folders

            selection.append(DbUtil.createIN("parentId", filter.hiddenFolders.size))
            filter.hiddenFolders.mapTo(selectionArgs) { it }    // FIXME: Should be Long
        }

        order.append(" ORDER BY ")
        val direction = if (filter.sortAscending) " ASC" else " DESC"

        if (filter.segregateByType) {
            order.append(Meta.TYPE).append(" COLLATE NOCASE").append(" ASC, ")
        }
        when (filter.sortColumn) {
            XmpFilter.SortColumns.Date -> order.append(Meta.TIMESTAMP).append(direction)
            XmpFilter.SortColumns.Name -> order.append(Meta.NAME).append(" COLLATE NOCASE").append(direction)
        }

        query.append(coreQuery)
        if (selection.isNotEmpty()) {
            query.append(" WHERE ")
            query.append(selection)
        }
        query.append(group)
        query.append(order)

        return SimpleSQLiteQuery(query.toString(), selectionArgs.toArray())
    }
}
