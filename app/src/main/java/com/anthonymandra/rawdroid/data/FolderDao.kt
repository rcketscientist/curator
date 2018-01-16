package com.anthonymandra.rawdroid.data

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.Dao
import android.arch.persistence.room.Delete
import android.arch.persistence.room.Insert
import android.arch.persistence.room.Query
import android.arch.persistence.room.Update

@Dao
abstract class FolderDao : PathDao<FolderEntity>() {

    @get:Query("SELECT * FROM image_parent")
    abstract val all: LiveData<List<FolderEntity>>

    @Query("SELECT COUNT(*) FROM image_parent")
    abstract fun count(): Int

    @Query("SELECT * FROM image_parent WHERE id = :id")
    abstract override fun get(id: Long): FolderEntity

    @Query("SELECT id FROM image_parent WHERE path LIKE :path || '%'")
    abstract override fun getDescendantIds(path: String): List<Long>

    @Query("SELECT id FROM image_parent WHERE :path LIKE path || '%'")
    abstract override fun getAncestorIds(path: String): List<Long>
}
