package com.anthonymandra.rawdroid.data

import android.arch.lifecycle.LiveData
import android.arch.paging.DataSource
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

    val allMetadata: LiveData<List<MetadataTest>> = getImages()

    @Query("SELECT COUNT(*) FROM meta")
    abstract fun count(): Int

    @RawQuery(observedEntities = [ MetadataEntity::class, SubjectJunction::class ])
    internal abstract fun internalGetImages(query: SupportSQLiteQuery): LiveData<List<MetadataTest>>

//    @RawQuery(observedEntities = [ MetadataEntity::class, FolderEntity::class, SubjectJunction::class ])
//    internal abstract fun internalGetImageSource(query: SupportSQLiteQuery): DataSource.Factory<Int, MetadataTest>

    @RawQuery(observedEntities = [ MetadataEntity::class/*, SubjectJunction::class*/ ])
    internal abstract fun internalGetImageFactory(query: SupportSQLiteQuery): DataSource.Factory<Int, MetadataTest>

    @Query("SELECT * FROM meta WHERE id IN (:ids)")
    internal abstract fun getImagesById(ids: List<Long>): DataSource.Factory<Int, MetadataTest>

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

    @Query("DELETE FROM meta WHERE id = :id")
    abstract fun delete(id: Long)

    @Query("DELETE FROM meta WHERE id IN (:ids)")
    abstract fun delete(ids: List<Long>)

    @Query("DELETE FROM meta")
    abstract fun deleteAll()

    companion object {
        /**
         * If using a relation for subject this is a far simpler solution.
         * Requires [groupBy]
         */
        private const val relationQuery =
            "SELECT *" +
            " FROM meta" +
            " LEFT JOIN meta_subject_junction ON meta_subject_junction.metaId = meta.id"

        private const val groupBy = " GROUP BY meta.id"
    }

    // TODO: We could potentially use a static source in viewer to maintain order and update the
    // appropriate meta tables when edited.
//    fun getImageFactory(filter: XmpFilter) : PositionalDataSource<MetadataTest> {
//        return internalGetImageFactory(this.createFilterQuery(filter)).create()
//    }

    fun getImageFactory(filter: XmpFilter) : DataSource.Factory<Int, MetadataTest> {
        return internalGetImageFactory(this.createFilterQuery(filter))
    }

    fun getImageFactory() : DataSource.Factory<Int, MetadataTest> {
        return getImageFactory(XmpFilter())
    }

    fun getImages(filter: XmpFilter) : LiveData<List<MetadataTest>> {
        return internalGetImages(this.createFilterQuery(filter))
    }

    /**
     * get with default filter will return all with default sorting
     */
    private fun getImages() : LiveData<List<MetadataTest>> {
        return getImages(XmpFilter())
    }

    private fun createFilterQuery(filter: XmpFilter): SupportSQLiteQuery {
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
        query.append(groupBy)
        query.append(order)

        return SimpleSQLiteQuery(query.toString(), selectionArgs.toArray())
    }
}
