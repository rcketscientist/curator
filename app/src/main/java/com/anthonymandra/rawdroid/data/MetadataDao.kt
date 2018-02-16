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

//    @get:Query("SELECT * FROM meta")
//    abstract val all: LiveData<List<MetadataEntity>>

    val all: LiveData<List<MetadataResult>> = getImages()

    // --- AND ----
    // --- NAME ---

    @get:Query(mergeQuery)
    internal abstract val images: LiveData<List<MetadataResult>>

    @get:Query(mergeQuery + "WHERE label IN (\"label1\")")
    internal abstract val imagesTest: LiveData<List<MetadataResult>>

    @Query("SELECT COUNT(*) FROM meta")
    abstract fun count(): Int

    @RawQuery(observedEntities = [ MetadataEntity::class, FolderEntity::class, SubjectJunction::class ])
    internal abstract fun internalGetImages(query: SupportSQLiteQuery): LiveData<List<MetadataResult>>

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

        private const val mergeTables =
            "(SELECT GROUP_CONCAT(name) " +
            "FROM meta_subject_junction " +
            "JOIN xmp_subject " +
            "ON xmp_subject.id = meta_subject_junction.subjectId " +
            "WHERE meta_subject_junction.metaId = meta.id) AS keywords, " +
            "(SELECT documentUri " +
            "FROM image_parent " +
            "WHERE meta.parentId = image_parent.id ) AS parentUri "

        private const val coreQuery =
            "SELECT *, " +
            "(SELECT GROUP_CONCAT(name) " +
                "FROM meta_subject_junction " +
                "JOIN xmp_subject " +
                "ON xmp_subject.id = meta_subject_junction.subjectId " +
                "WHERE meta_subject_junction.metaId = meta.id) AS keywords, " +
            "(SELECT documentUri " +
                "FROM image_parent " +
                "WHERE meta.parentId = image_parent.id ) AS parentUri "

        private const val fromQuery = " FROM meta"

        // Subject ID instead of name
        private const val coreQuery2 =
            "SELECT * FROM meta " +
            "(SELECT GROUP_CONCAT(name) " +
                "FROM meta_subject_junction " +
                "JOIN xmp_subject " +
                "ON xmp_subject.id = meta_subject_junction.subjectId " +
                "WHERE meta_subject_junction.metaId = meta.id) AS keywords, " +
            "(SELECT documentUri " +
                "FROM image_parent " +
                "WHERE meta.parentId = image_parent.id ) AS parentUri "

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

    /**
     * get with default filter will return all with default sorting
     */
    private fun getImages() : LiveData<List<MetadataResult>> {
        return getImages(XmpFilter())
    }

    @Query("SELECT * FROM meta")
//    internal abstract fun getWithRelations(): LiveData<List<MetadataXmp>>

    private fun createFilterQuery(filter: XmpFilter): SupportSQLiteQuery {
        val selection = StringBuilder()
        val selectionArgs = ArrayList<Any>()
        var requiresJoiner = false

        val and = " AND "
        val or = " OR "
        val joiner = if (filter.andTrueOrFalse) and else or

        selection.append(mergeQuery)
        if (filter.xmp != null) {
            if (filter.xmp.label.isNotEmpty()) {
                requiresJoiner = true

                selection.append(DbUtil.createIN(Meta.LABEL, filter.xmp.label.size))
                selectionArgs.addAll(filter.xmp.label)
            }

            if (filter.xmp.subject.isNotEmpty()) {
                if (requiresJoiner)
                    selection.append(joiner)
                requiresJoiner = true

                selection.append(DbUtil.createIN("subjectId", filter.xmp.subject.size))
                filter.xmp.subject.mapTo(selectionArgs) { it.id }
            }

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
                selection.append(and)   // Always exclude the folders, don't OR
            selection.append(" NOT" )   // Not in hidden folders

            selection.append(DbUtil.createIN("parentId", filter.hiddenFolders.size))
            filter.hiddenFolders.mapTo(selectionArgs) { it }    // FIXME: Should be Long
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

        selection.append(" ORDER BY ")
        selection.append(sort)
        return SimpleSQLiteQuery(selection.toString(), selectionArgs.toArray())
    }
}
