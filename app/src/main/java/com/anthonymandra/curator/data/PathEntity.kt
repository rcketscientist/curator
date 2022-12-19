package com.anthonymandra.curator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
abstract class PathEntity {
    /**
     * Primary key
     */
    @PrimaryKey(autoGenerate = true)
    open var id: Long = 0

    /**
     * Path identifying the hierarchy of data
     */
    open var path: String = ""

    /**
     * This item's parent id or null if it is a root
     */
    open var parent: Long = -1

    /**
     * The depth at which this item resides (number of parents)
     */
    open var depth: Int = 0

    fun hasParent() = parent > 0
}
