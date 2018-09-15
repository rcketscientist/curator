@file:Suppress("FunctionName")

package com.anthonymandra.rawdroid.data

import androidx.lifecycle.LiveData
import androidx.paging.DataSource
import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.anthonymandra.content.Meta
import com.anthonymandra.rawdroid.XmpFilter
import com.anthonymandra.util.AppExecutors
import com.anthonymandra.util.DbUtil
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import java.util.ArrayList

/**
 * get with default filter will return all with default sorting
 */
//private fun getImages() : LiveData<List<MetadataTest>> {
//    return getImages(XmpFilter())
//}
/**
 * Repository handling the work with products and comments.
 */
class DataRepository private constructor(private val database: AppDatabase) {
    // ---- Pure meta database calls -------
    @WorkerThread
    fun _images(uris: Array<String>) = database.metadataDao()._images(uris)
    @WorkerThread
    fun _images(ids: LongArray) = database.metadataDao()._images(ids)

    fun images(uris: List<String>) = database.metadataDao().stream(uris)

    @WorkerThread
    fun _image(uri: String) = database.metadataDao()._images(uri)
    fun image(uri: String) = database.metadataDao()[uri]    // instead of get...weird

    fun getImageCount(filter: XmpFilter = XmpFilter()) : LiveData<Int> {
        return database.metadataDao().count(createFilterQuery(countQuery(filter)))
    }

    fun getUnprocessedCount(filter: XmpFilter = XmpFilter()) : LiveData<Int> {
        return database.metadataDao().count(createFilterQuery(countUnprocessedQuery(filter)))
    }

    fun getProcessedCount(filter: XmpFilter  = XmpFilter()) : LiveData<Int> {
        return database.metadataDao().count(createFilterQuery(countProcessedQuery(filter)))
    }

    fun getUnprocessedImages(filter: XmpFilter = XmpFilter()) : LiveData<List<MetadataTest>> {
        return database.metadataDao().getImages(createFilterQuery(imageUnprocessedQuery(filter)))
    }

    @WorkerThread
    fun _getUnprocessedImages(filter: XmpFilter = XmpFilter()) : List<MetadataTest> {
        return database.metadataDao().imageBlocking(createFilterQuery(imageUnprocessedQuery(filter)))
    }

    fun insertImages(vararg entity: MetadataEntity) = database.metadataDao().insert(*entity)

    fun deleteImage(id: Long) {
        Completable.fromAction { database.metadataDao().delete(id) }
            .subscribeOn(Schedulers.from(AppExecutors.DISK))
            .subscribe()
    }

    fun deleteImage(image: MetadataTest) {
        Completable.fromAction { database.metadataDao().delete(image) }
                .subscribeOn(Schedulers.from(AppExecutors.DISK))
                .subscribe()
    }

    fun deleteAllImages() {
        Completable.fromAction { database.metadataDao().deleteAll() }
            .subscribeOn(Schedulers.from(AppExecutors.DISK))
            .subscribe()
    }

    // ---- Pure subject database calls ----
    fun convertToSubjectIds(subjects: List<String>) : List<Long> {
        return database.subjectDao().idsForNames(subjects)
    }

    fun getChildSubjects(path: String): Single<List<SubjectEntity>> {
        return Single.create<List<SubjectEntity>> { emitter ->
            val descendants = database.subjectDao().getDescendants(path)
            emitter.onSuccess(descendants)
        }.subscribeOn(Schedulers.from(AppExecutors.DISK))
    }

    fun getParentSubjects(path: String): Single<List<SubjectEntity>> {
        return Single.create<List<SubjectEntity>> { emitter ->
            val ancestors = database.subjectDao().getAncestors(path)
            emitter.onSuccess(ancestors)
        }.subscribeOn(Schedulers.from(AppExecutors.DISK))
    }

    // ---- Pure folder database calls -----
    val parents get() = database.folderDao().parents
    val lifecycleParents get() = database.folderDao().lifecycleParents

    val streamParents get() = database.folderDao().streamParents

    fun insertParent(entity: FolderEntity) = database.folderDao().insert(entity)
    fun insertParents(vararg folders: FolderEntity): Completable {
        return Completable.create {
            database.folderDao().insert(*folders)
            it.onComplete()
        }.subscribeOn(Schedulers.from(AppExecutors.DISK))
    }

    fun updateParents(vararg folders: FolderEntity): Completable {
        return Completable.create {
            database.folderDao().update(*folders)
            it.onComplete()
        }.subscribeOn(Schedulers.from(AppExecutors.DISK))
    }

    // Note: folders aren't even a path enumeration anymore
//    fun getChildFolders(path: String): Single<List<FolderEntity>> {
//        return Single.create<List<FolderEntity>> { emitter ->
//            val descendants = database.folderDao().getDescendants(path)
//            emitter.onSuccess(descendants)
//        }.subscribeOn(Schedulers.from(AppExecutors.DISK))
//    }
//
//    fun getParentFolders(path: String): Single<List<FolderEntity>> {
//        return Single.create<List<FolderEntity>> { emitter ->
//            val ancestors = database.folderDao().getAncestors(path)
//            emitter.onSuccess(ancestors)
//        }.subscribeOn(Schedulers.from(AppExecutors.DISK))
//    }

    // ---- Hybrid database calls ----------

    fun getGalleryLiveData(filter: XmpFilter): DataSource.Factory<Int, MetadataTest> {
        return database.metadataDao().getImageFactory(createFilterQuery(filter))
    }

    fun getImages(filter: XmpFilter) : LiveData<List<MetadataTest>> {
        return database.metadataDao().getImages(createFilterQuery(filter))
    }

    /**
     * get with default filter will return all with default sorting
     */
    private fun getImages() : LiveData<List<MetadataTest>> {
        return getImages(XmpFilter())
    }

    fun insertMeta(vararg inserts: MetadataTest) : List<Long> {
        val subjectMapping = mutableListOf<SubjectJunction>()
         inserts.forEach { image ->
            image.subjectIds.mapTo(subjectMapping) { SubjectJunction(image.id, it)}
        }
        if (!subjectMapping.isEmpty()) {
            database.subjectJunctionDao().insert(*subjectMapping.toTypedArray())
        }
        return database.metadataDao().replace(*inserts)
    }

    fun updateMeta(vararg images: MetadataTest) : Completable {
        return Completable.create{
            val subjectMapping = mutableListOf<SubjectJunction>()

            // SQLite has a var limit of 999
            images.asIterable().chunked(999).forEach { updates ->
                // We clear the existing subject map for each image
                database.subjectJunctionDao().delete(updates.map { it.id })

                // Update the subject map
                updates.forEach { image ->
                    image.subjectIds.mapTo(subjectMapping) { SubjectJunction(image.id, it) }
                }
                if (!subjectMapping.isEmpty()) {
                    database.subjectJunctionDao().insert(*subjectMapping.toTypedArray())
                }

                // Update that image table
                database.metadataDao().update(*updates.toTypedArray())
            }
            it.onComplete()
        }.subscribeOn(Schedulers.from(AppExecutors.DISK))
    }

    companion object {

        @Volatile private var INSTANCE: DataRepository? = null

        fun getInstance(database: AppDatabase): DataRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?:DataRepository(database)
                        .also { INSTANCE = it }
            }

        fun getInstance(context: Context): DataRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?:DataRepository(AppDatabase.getInstance(context))
                        .also { INSTANCE = it }
            }

        /**
         * Joins meta and subject
         */
        private const val junctionJoin = "FROM meta LEFT JOIN meta_subject_junction ON meta_subject_junction.metaId = meta.id"
        private const val whereUnprocessed = "meta.processed = 0"
        private const val whereProcessed = "meta.processed = 1"

         // The following require [groupBy]
        private const val imageSelect = "SELECT * $junctionJoin"

        // Count must not group!
        private const val countSelect = "SELECT COUNT(*) $junctionJoin"

        private const val groupBy = " GROUP BY meta.id"

        data class Query(
                val filter: XmpFilter = XmpFilter(),
                val coreQuery: String = imageSelect,
                val where: Array<String> = emptyArray(),
                val whereArgs: Array<String> = emptyArray(),
                val applyGroup: Boolean = true
        )

        private fun imageQuery(filter: XmpFilter) =
                Query(filter)
        private fun imageProcessedQuery(filter: XmpFilter) =
                Query(filter, where = arrayOf(whereProcessed))
        private fun imageUnprocessedQuery(filter: XmpFilter) =
                Query(filter, where = arrayOf(whereUnprocessed))

        private fun countQuery(filter: XmpFilter) =
                Query(filter, coreQuery = countSelect, applyGroup = false)
        private fun countProcessedQuery(filter: XmpFilter) =
                Query(filter, coreQuery = countSelect, where = arrayOf(whereProcessed), applyGroup = false)
        private fun countUnprocessedQuery(filter: XmpFilter) =
                Query(filter, coreQuery = countSelect, where = arrayOf(whereUnprocessed), applyGroup = false)

        fun createFilterQuery(query: Query): SupportSQLiteQuery {
            return createFilterQuery(query.filter, query.coreQuery, query.where, query.whereArgs, query.applyGroup)
        }
        fun createFilterQuery(filter: XmpFilter = XmpFilter(),
                              coreQuery: String = imageSelect,
                              where: Array<String> = emptyArray(),
                              whereArgs: Array<String> = emptyArray(),
                              applyGroup: Boolean = true): SupportSQLiteQuery {
            val query = StringBuilder()
            val selection = StringBuilder()
            val order = StringBuilder()
            val selectionArgs = ArrayList<Any>()

            val and = " AND "
            val or = " OR "
            val joiner = if (filter.andTrueOrFalse) and else or

            // Special case, append to join query
            if (filter.subjectIds.isNotEmpty()) {
                if (!selection.isEmpty())
                    selection.append(joiner)

                selection.append(DbUtil.createIN("subjectId", filter.subjectIds.size))
                selectionArgs.addAll(filter.subjectIds)
            }

            if (filter.labels.isNotEmpty()) {
                if (!selection.isEmpty())
                    selection.append(joiner)

                selection.append(DbUtil.createIN(Meta.LABEL, filter.labels.size))
                selectionArgs.addAll(filter.labels)
            }

            if (filter.ratings.isNotEmpty()) {
                if (!selection.isEmpty())
                    selection.append(joiner)

                selection.append(DbUtil.createIN(Meta.RATING, filter.ratings.size))
                filter.ratings.mapTo(selectionArgs) { java.lang.Double.toString(it.toDouble()) }
            }

            if (filter.hiddenFolderIds.isNotEmpty()) {
                if (selection.isNotEmpty())
                    selection.append(and)       // Always exclude the folders, don't OR

                selection.append(DbUtil.createIN("parentId", filter.hiddenFolderIds, true))
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
            if (selection.isNotEmpty() || where.isNotEmpty()) {
                query.append(" WHERE ")
                query.append(selection)

                if (selection.isNotEmpty() && where.isNotEmpty())
                    query.append(" AND ")

                where.forEachIndexed { index, value ->
                    if (index > 0)
                        query.append(" AND ")
                    query.append(value)
                }
            }

            // For the most part we avoid group on counts
            if (applyGroup) {
                query.append(groupBy)
            }

            query.append(order)

            selectionArgs.addAll(whereArgs)

            Log.d("TEST", query.toString())

            return SimpleSQLiteQuery(query.toString(), selectionArgs.toArray())
        }
    }
}
