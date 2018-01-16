package com.anthonymandra.rawdroid.data

import android.arch.persistence.room.Delete
import android.arch.persistence.room.Insert
import android.arch.persistence.room.Update

abstract class PathDao<T : PathEntity> {

    /**
     * Annotate the implementation with:
     *
     *
     * Query("SELECT * FROM [DATABASE_NAME] WHERE id= :id ")
     * @param id
     * @return
     */
    internal abstract fun get(id: Long): T

    @Insert
    internal abstract fun insertInternal(vararg entities: T): Long
    @Update
    internal abstract fun update(vararg entities: T)
    @Delete
    abstract fun delete(vararg entities: T)

    /**
     * Annotate the implementation with:
     *
     *
     * Query("SELECT id FROM [DATABASE_NAME] WHERE :path LIKE path || '%'")
     * @param path path to find
     * @return ancestors of path
     */
    abstract fun getAncestorIds(path: String): List<Long>

    /**
     * Annotate the implementation with:
     *
     *
     * Query("SELECT id FROM [DATABASE_NAME] WHERE path LIKE :path || '%'")
     * @param path path to find
     * @return ancestors of path
     */
    abstract fun getDescendantIds(path: String): List<Long>

    fun insert(entity: T): Long {
        var parentPath = PATH_DELIMITER  // Default path defines a root node
        var parentDepth = -1

        if (entity.hasParent()) {
            val parent = get(entity.parent)
            parentPath = parent.path + PATH_DELIMITER
            parentDepth = parent.depth
        }

        // Would we want path to be unique?
        val childId = insertInternal(entity)
        if (childId == -1L)
            return -1

        // Update the child entry with its full path
        entity.id = childId
        entity.path = parentPath + childId
        entity.depth = parentDepth + 1

        update(entity)
        return childId
    }

    fun getDescendantIds(id: Long): List<Long> {
        val pd = get(id)
        return getDescendantIds(pd.path)
    }

    fun getAncestorIds(id: Long): List<Long> {
        val pd = get(id)
        return getAncestorIds(pd.path)
    }

    companion object {
        private val PATH_DELIMITER = "/"
    }
}
