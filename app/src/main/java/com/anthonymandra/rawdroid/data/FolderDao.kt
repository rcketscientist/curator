package com.anthonymandra.rawdroid.data

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.Dao
import android.arch.persistence.room.Query

@Dao
abstract class FolderDao : PathDao<FolderEntity>() {
    override val database: String
        get() = "image_parent"

    @get:Query("SELECT * FROM image_parent")
    abstract val all: LiveData<List<FolderEntity>>

    @Query("SELECT COUNT(*) FROM image_parent")
    abstract fun count(): Int
}
