package com.anthonymandra.rawdroid.data

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*
import io.reactivex.Flowable

@Dao
abstract class FolderDao {//} : PathDao<FolderEntity>() {
//    override val database: String
//        get() = "image_parent"

    @get:Query("SELECT * FROM image_parent")
    abstract val lifecycleParents: LiveData<List<FolderEntity>>

    @get:Query("SELECT * FROM image_parent")
    abstract val streamParents: Flowable<List<FolderEntity>>

    @Query("SELECT COUNT(*) FROM image_parent")
    abstract fun count(): Int

    @Query("SELECT * FROM image_parent WHERE id = :id")
    abstract fun get(id: Long) : FolderEntity

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insert(row: FolderEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insert(vararg datums: FolderEntity)

    @Update
    abstract fun update(row: FolderEntity): Int

    @Update
    abstract fun update(vararg entities: FolderEntity): Int

    @Delete
    abstract fun delete(vararg entities: FolderEntity): Int
}
