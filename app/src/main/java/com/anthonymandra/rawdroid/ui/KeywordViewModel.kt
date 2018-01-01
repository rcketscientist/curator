package com.anthonymandra.rawdroid.ui

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import com.anthonymandra.rawdroid.App
import com.anthonymandra.rawdroid.data.SubjectEntity
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers

class KeywordViewModel(app: Application) : AndroidViewModel(app) {

    // MediatorLiveData can observe other LiveData objects and react on their emissions.
    private val mObservableProducts: MediatorLiveData<List<SubjectEntity>> = MediatorLiveData()
    private val dataSource = (app as App).database.subjectDao()
    private val executors = (app as App).appExecutors

    /**
     * Expose the LiveData keywords query so the UI can observe it.
     */
    val keywords: LiveData<List<SubjectEntity>>
        get() = mObservableProducts

    fun getDescendants(path: String): Single<List<SubjectEntity>> {
        return Single.create<List<SubjectEntity>> { emitter ->
            val descendants = dataSource.getDescendants(path)
            emitter.onSuccess(descendants)
        }.subscribeOn(Schedulers.from(executors.diskIO()))
    }

    fun getAncestors(path: String): Single<List<SubjectEntity>> {
        return Single.create<List<SubjectEntity>> { emitter ->
            val ancestors = dataSource.getAncestors(path)
            emitter.onSuccess(ancestors)
        }.subscribeOn(Schedulers.from(executors.diskIO()))
    }

    /**
     * Update the keywords on the IO thread
     */
    fun update(keywords: List<SubjectEntity>) {
        Completable.fromAction {
            dataSource.update(keywords)
        }.subscribeOn(Schedulers.from(executors.diskIO())).subscribe()
    }

    init {
        // set by default null, until we get data from the database.
        mObservableProducts.value = null

        val keywords = dataSource.all

        // observe the changes of the products from the database and forward them
        mObservableProducts.addSource<List<SubjectEntity>>(keywords, { mObservableProducts.setValue(it) })
    }
}