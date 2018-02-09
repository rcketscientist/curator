package com.anthonymandra.rawdroid.data

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import com.anthonymandra.util.AppExecutors
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers

/**
 * Repository handling the work with products and comments.
 */
class DataRepository private constructor(private val database: AppDatabase) {
    private val metaStream: MediatorLiveData<List<MetadataResult>> = MediatorLiveData()
    private val subjectStream: MediatorLiveData<List<SubjectEntity>> = MediatorLiveData()

    init {
        // set by default null, until we get data from the database.
        subjectStream.value = null
        metaStream.value = null

        // Initialize
        val subjects = database.subjectDao().all
        val meta = database.metadataDao().all

        // observe the changes of the products from the database and forward them
        subjectStream.addSource<List<SubjectEntity>>(subjects, { subjectStream.setValue(it) })
        metaStream.addSource<List<MetadataResult>>(meta, { metaStream.setValue(it) })
    }

    val keywords: LiveData<List<SubjectEntity>>
        get() = subjectStream

    val meta: LiveData<List<MetadataResult>>
        get() = metaStream

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

    fun getChildFolders(path: String): Single<List<FolderEntity>> {
        return Single.create<List<FolderEntity>> { emitter ->
            val descendants = database.folderDao().getDescendants(path)
            emitter.onSuccess(descendants)
        }.subscribeOn(Schedulers.from(AppExecutors.DISK))
    }

    fun getParentFolders(path: String): Single<List<FolderEntity>> {
        return Single.create<List<FolderEntity>> { emitter ->
            val ancestors = database.folderDao().getAncestors(path)
            emitter.onSuccess(ancestors)
        }.subscribeOn(Schedulers.from(AppExecutors.DISK))
    }

    fun insertMeta(vararg inserts: MetadataEntity) : List<Long> {
        inserts.forEach {
            if (it is MetadataResult) {
                it.keywords.forEach {
                    database.subjectJunctionDao().insert(SubjectJunction(key))
                }
            }
            return database.metadataDao().insert(*inserts)
        }
    }

    fun updateMeta(vararg inserts: MetadataEntity) : List<Long> {
        inserts.forEach {
            if (it is MetadataResult) {
                it.keywords.forEach {
                    database.subjectJunctionDao().insert(SubjectJunction(key))
                }
            }
            return database.metadataDao().insert(*inserts)
        }
    }

    companion object {

        @Volatile private var INSTANCE: DataRepository? = null

        fun getInstance(database: AppDatabase): DataRepository =
                INSTANCE ?: synchronized(this) {
                    INSTANCE ?: INSTANCE = DataRepository(database).also { INSTANCE = it }
                }
    }
}
