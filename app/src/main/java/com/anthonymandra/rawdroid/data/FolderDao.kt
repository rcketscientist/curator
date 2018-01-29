package com.anthonymandra.rawdroid.data

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.Dao
import android.arch.persistence.room.Query

@Dao
abstract class FolderDao : PathDao<FolderEntity>() {

    override fun getDatabase() = "image_parent"
    @get:Query("SELECT * FROM image_parent")
    abstract val all: LiveData<List<FolderEntity>>

    @Query("SELECT COUNT(*) FROM image_parent")
    abstract fun count(): Int
//    @Query("SELECT * FROM image_parent WHERE id = :id")
//    abstract override fun internalGet(id: Long): FolderEntity
//
//    @Query("SELECT id FROM image_parent WHERE path LIKE :path || '%'")
//    abstract override fun internalGetDescendantIds(path: String): List<Long>
//
//    @Query("SELECT id FROM image_parent WHERE :path LIKE path || '%'")
//    abstract override fun internalGetAncestorIds(path: String): List<Long>
}
