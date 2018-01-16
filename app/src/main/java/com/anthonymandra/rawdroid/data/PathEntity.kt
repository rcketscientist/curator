package com.anthonymandra.rawdroid.data

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey

@Entity
abstract class PathEntity @JvmOverloads constructor(
    /**
     * Primary key
     */
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,

    /**
     * Path identifying the hierarchy of data
     */
    var path: String = "",

    /**
     * This item's parent id or null if it is a root
     */
    var parent: Long = -1,

    /**
     * The depth at which this item resides (number of parents)
     */
    var depth: Int = 0) {

    fun hasParent() = parent > 0
}
