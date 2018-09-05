package com.anthonymandra.rawdroid.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.paging.DataSource
import android.content.Context
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


//fun getImageFactory(filter: XmpFilter) : DataSource.Factory<Int, MetadataTest> {
////    return internalGetImageFactory(this.createFilterQuery(filter))
////}
////
////fun getImageFactory() : DataSource.Factory<Int, MetadataTest> {
////    return getImageFactory(XmpFilter())
////}
////
////fun getImages(filter: XmpFilter) : LiveData<List<MetadataTest>> {
////    return internalGetImages(this.createFilterQuery(filter))
////}

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
    fun imagesBlocking(uris: List<String>) = database.metadataDao().blocking(uris)
    fun images(uris: List<String>) = database.metadataDao().stream(uris)
    fun imageBlocking(uri: String) = database.metadataDao().blocking(uri)
    fun image(uri: String) = database.metadataDao()[uri]    // instead of get...weird
    fun getProcessedCount() = database.metadataDao().processedCount()
    fun getCount() = database.metadataDao().count()

    fun insertImages(vararg entity: MetadataEntity) = database.metadataDao().insert(*entity)

    fun unprocessedImages() = database.metadataDao().unprocessedImages()

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
    val lifecycleParents = database.folderDao().lifecycleParents

    val streamParents = database.folderDao().streamParents

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
        return database.metadataDao().insert(*inserts)
    }

    fun updateMeta(vararg updates: MetadataTest) : Completable {
        return Completable.create{
            val subjectMapping = mutableListOf<SubjectJunction>()

            // We clear the existing subject map for each image
            database.subjectJunctionDao().delete(updates.map { it.id })

            // Update the subject map
            updates.forEach { image ->
                image.subjectIds.mapTo(subjectMapping) { SubjectJunction(image.id, it)}
            }
            if (!subjectMapping.isEmpty()) {
                database.subjectJunctionDao().insert(*subjectMapping.toTypedArray())
            }

            // Update that image table
            database.metadataDao().update(*updates)
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
         * If using a relation for subject this is a far simpler solution.
         * Requires [groupBy]
         */
        private const val imageSubjectQuery =
                "SELECT *" +
                        " FROM meta" +
                        " LEFT JOIN meta_subject_junction ON meta_subject_junction.metaId = meta.id"
        private const val groupBy = " GROUP BY meta.id"

        fun createFilterQuery(filter: XmpFilter): SupportSQLiteQuery {
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
                if (selection.isNotEmpty())
                    selection.append(and)       // Always exclude the folders, don't OR

                selection.append(DbUtil.createIN("parentId", filter.hiddenFolders, true))
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

            query.append(imageSubjectQuery)
            if (selection.isNotEmpty()) {
                query.append(" WHERE ")
                query.append(selection)
            }
            query.append(groupBy)
            query.append(order)

            return SimpleSQLiteQuery(query.toString(), selectionArgs.toArray())
        }
    }
}
