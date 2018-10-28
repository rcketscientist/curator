@file:Suppress("FunctionName")

package com.anthonymandra.rawdroid.data

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.paging.DataSource
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.anthonymandra.content.Meta
import com.anthonymandra.rawdroid.ImageFilter
import com.anthonymandra.util.AppExecutors
import com.anthonymandra.util.DbUtil
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import java.util.*

/**
 * Repository handling the work with products and comments.
 */
class DataRepository private constructor(private val database: AppDatabase) {
	// ---- Pure meta database calls -------
	@WorkerThread
	fun synchImage(uri: String) = database.metadataDao().synchImage(uri)

	@WorkerThread
	fun synchImages() = database.metadataDao().synchAllImages

	@WorkerThread
	fun synchImages(uris: Array<String>) = database.metadataDao().synchImages(uris)

	@WorkerThread
	fun synchImages(ids: LongArray) = database.metadataDao().synchImages(ids)

	@WorkerThread
	fun synchImageUris() = database.metadataDao().uris

	fun images(ids: LongArray): Single<List<ImageInfo>> =
			Single.create<List<ImageInfo>> { emitter ->
				emitter.onSuccess(database.metadataDao().synchImages(ids))
			}.subscribeOn(Schedulers.from(AppExecutors.DISK))


	fun images(uris: List<String>) = database.metadataDao().stream(uris)

	fun image(uri: String) = database.metadataDao()[uri]    // instead of get...weird

	fun selectAll(filter: ImageFilter = ImageFilter()): Single<LongArray> {
		return Single.create<LongArray> { emitter ->
			emitter.onSuccess(database.metadataDao().ids(createFilterQuery(idsQuery(filter))))
		}.subscribeOn(Schedulers.from(AppExecutors.DISK))
	}

	fun getImageCount(filter: ImageFilter = ImageFilter()): LiveData<Int> {
		return database.metadataDao().count(createFilterQuery(countQuery(filter)))
	}

	fun getUnprocessedCount(filter: ImageFilter = ImageFilter()): LiveData<Int> {
		return database.metadataDao().count(createFilterQuery(countUnprocessedQuery(filter)))
	}

	fun getProcessedCount(filter: ImageFilter = ImageFilter()): LiveData<Int> {
		return database.metadataDao().count(createFilterQuery(countProcessedQuery(filter)))
	}

	fun getUnprocessedImages(filter: ImageFilter = ImageFilter()): LiveData<List<ImageInfo>> {
		return database.metadataDao().getImages(createFilterQuery(imageUnprocessedQuery(filter)))
	}

	@WorkerThread
	fun _getUnprocessedImages(filter: ImageFilter = ImageFilter()): List<ImageInfo> {
		return database.metadataDao().imageBlocking(createFilterQuery(imageUnprocessedQuery(filter)))
	}

	@WorkerThread
	fun synchInsertImages(vararg entity: MetadataEntity) = database.metadataDao().insert(*entity)

	fun insertImages(vararg entity: MetadataEntity): Single<List<Long>> {
		return Single.create<List<Long>> { database.metadataDao().insert(*entity) }
				.subscribeOn(Schedulers.from(AppExecutors.DISK))
	}

	@WorkerThread
	fun deleteImages(uris: Array<String>) {
		database.metadataDao().delete(uris)
	}

	fun deleteImage(vararg id: Long) {
		Completable.fromAction { database.metadataDao().delete(*id) }
				.subscribeOn(Schedulers.from(AppExecutors.DISK))
				.subscribe()
	}

	fun deleteImage(image: ImageInfo) {
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
	fun convertToSubjectIds(subjects: List<String>): List<Long> {
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
		return Completable.fromAction { database.folderDao().insert(*folders) }
				.subscribeOn(Schedulers.from(AppExecutors.DISK))
	}

	fun updateParents(vararg folders: FolderEntity): Completable {
		return Completable.fromAction { database.folderDao().update(*folders)	}
				.subscribeOn(Schedulers.from(AppExecutors.DISK))
	}

	fun deleteParents(vararg folders: FolderEntity) {
		Completable.fromAction { database.folderDao().delete(*folders) }
				.subscribeOn(Schedulers.from(AppExecutors.DISK)).subscribe()
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

	fun getGalleryLiveData(filter: ImageFilter): DataSource.Factory<Int, ImageInfo> {
		return database.metadataDao().getImageFactory(createFilterQuery(filter))
	}

	fun getImages(filter: ImageFilter): LiveData<List<ImageInfo>> {
		return database.metadataDao().getImages(createFilterQuery(filter))
	}

	/**
	 * get with default filter will return all with default sorting
	 */
	private fun getImages(): LiveData<List<ImageInfo>> {
		return getImages(ImageFilter())
	}

	fun insertMeta(vararg inserts: ImageInfo): List<Long> {
		val subjectMapping = mutableListOf<SubjectJunction>()
		inserts.forEach { image ->
			image.subjectIds.mapTo(subjectMapping) { SubjectJunction(image.id, it) }
		}
		if (!subjectMapping.isEmpty()) {
			database.subjectJunctionDao().insert(*subjectMapping.toTypedArray())
		}
		return database.metadataDao().replace(*inserts)
	}

	fun updateMeta(vararg images: ImageInfo): Completable {
		return Completable.create { emitter ->
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
			emitter.onComplete()
		}.subscribeOn(Schedulers.from(AppExecutors.DISK))
	}

	companion object {

		@Volatile
		private var INSTANCE: DataRepository? = null

		fun getInstance(database: AppDatabase): DataRepository =
				INSTANCE ?: synchronized(this) {
					INSTANCE ?: DataRepository(database)
							.also { INSTANCE = it }
				}

		fun getInstance(context: Context): DataRepository =
				INSTANCE ?: synchronized(this) {
					INSTANCE ?: DataRepository(AppDatabase.getInstance(context))
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

		// The following require [groupBy]
		private const val idSelect = "SELECT meta.id $junctionJoin"

		// Count must not group!
		private const val countSelect = "SELECT COUNT(*) $junctionJoin"

		private const val groupBy = " GROUP BY meta.id"

		data class Query(
				val filter: ImageFilter = ImageFilter(),
				val coreQuery: String = imageSelect,
				val where: Array<String> = emptyArray(),
				val whereArgs: Array<String> = emptyArray(),
				val applyGroup: Boolean = true
		)

		private fun imageQuery(filter: ImageFilter) =
				Query(filter)

		private fun imageProcessedQuery(filter: ImageFilter) =
				Query(filter, where = arrayOf(whereProcessed))

		private fun imageUnprocessedQuery(filter: ImageFilter) =
				Query(filter, where = arrayOf(whereUnprocessed))

		private fun idsQuery(filter: ImageFilter) =
				Query(filter, coreQuery = idSelect, applyGroup = true)

		private fun countQuery(filter: ImageFilter) =
				Query(filter, coreQuery = countSelect, applyGroup = false)

		private fun countProcessedQuery(filter: ImageFilter) =
				Query(filter, coreQuery = countSelect, where = arrayOf(whereProcessed), applyGroup = false)

		private fun countUnprocessedQuery(filter: ImageFilter) =
				Query(filter, coreQuery = countSelect, where = arrayOf(whereUnprocessed), applyGroup = false)

		fun createFilterQuery(query: Query): SupportSQLiteQuery {
			return createFilterQuery(query.filter, query.coreQuery, query.where, query.whereArgs, query.applyGroup)
		}

		fun createFilterQuery(
				filter: ImageFilter = ImageFilter(),
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
				ImageFilter.SortColumns.Date -> order.append(Meta.TIMESTAMP).append(direction)
				ImageFilter.SortColumns.Name -> order.append(Meta.NAME).append(" COLLATE NOCASE").append(direction)
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

			return SimpleSQLiteQuery(query.toString(), selectionArgs.toArray())
		}
	}
}
