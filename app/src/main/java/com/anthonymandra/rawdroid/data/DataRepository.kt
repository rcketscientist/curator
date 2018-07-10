package com.anthonymandra.rawdroid.data

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.paging.DataSource
import android.content.Context
import com.anthonymandra.rawdroid.XmpFilter
import com.anthonymandra.util.AppExecutors
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers

/**
 * Repository handling the work with products and comments.
 */
class DataRepository private constructor(private val database: AppDatabase) {
    private val metaStream: MediatorLiveData<List<MetadataTest>> = MediatorLiveData()
    private val subjectStream: MediatorLiveData<List<SubjectEntity>> = MediatorLiveData()

    var galleryStream: DataSource.Factory<Int, MetadataTest> = database.metadataDao().getImageFactory()
    fun updateGalleryStream(filter: XmpFilter) {
        galleryStream = database.metadataDao().getImageFactory(filter)
    }
    fun updateGalleryStream(ids: List<Long>) {
        galleryStream = database.metadataDao().getImagesById(ids)
    }

    init {
        // set by default null, until we get data from the database.
        subjectStream.value = null
        metaStream.value = null

        // Initialize
        val subjects = database.subjectDao().all
        val meta = database.metadataDao().allMetadata

        // observe the changes of the products from the database and forward them
        subjectStream.addSource<List<SubjectEntity>>(subjects, { subjectStream.setValue(it) })
        metaStream.addSource<List<MetadataTest>>(meta, { metaStream.setValue(it) })
    }

    // ---- Pure meta database calls -------
    fun imagesBlocking(uris: List<String>) = database.metadataDao().blocking(uris)
    fun images(uris: List<String>) = database.metadataDao().stream(uris)
    fun imageBlocking(uri: String) = database.metadataDao().blocking(uri)
    fun image(uri: String) = database.metadataDao()[uri]    // instead of get...weird

    val meta: LiveData<List<MetadataTest>>
        get() = metaStream

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
    val keywords: LiveData<List<SubjectEntity>>
        get() = subjectStream

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
    }
}
