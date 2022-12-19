package com.anthonymandra.curator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recycle_bin")
data class RecycleBinEntity(
    /**
     * Primary key
     */
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,

    /**
     * Path of source for restore
     */
    var path: String = ""
)
