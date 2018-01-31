package com.anthonymandra.rawdroid.data

import android.arch.persistence.room.Delete
import android.arch.persistence.room.Insert
import android.arch.persistence.room.RawQuery
import android.arch.persistence.room.Update

abstract class PathDao<T : PathEntity> {
    protected abstract val database: String
    private val PATH_DELIMITER = "/"
    private val SELECT_ID = "SELECT id "
    private val SELECT_ALL = "SELECT * "
    private val FROM = "FROM " + database
    private val GET = "SELECT * $FROM WHERE id = %s "
    private val ANCESTOR_ID = SELECT_ID + FROM + " WHERE '%s' LIKE path || '%%' "
    private val DESCENDANT_ID = SELECT_ID + FROM + " WHERE path LIKE '%s' || '%%' "
    private val ANCESTOR_ENTITY = SELECT_ALL + FROM + " WHERE '%s' LIKE path || '%%' "
    private val DESCENDANT_ENTITY = SELECT_ALL + FROM + " WHERE path LIKE '%s' || '%%' "

    @RawQuery
    protected abstract fun internalGet(query: String): T

    @RawQuery
    protected abstract fun internalGetAncestorIds(query: String): List<Long>

    @RawQuery
    protected abstract fun internalGetDescendantIds(query: String): List<Long>

    @RawQuery
    protected abstract fun internalGetAncestors(query: String): List<T>

    @RawQuery
    protected abstract fun internalGetDescendants(query: String): List<T>

    operator fun get(id: Long?): T {
        return internalGet(String.format(GET, id))
    }

    @Insert
    protected abstract fun insertInternal(row: T): Long

    @Update
    abstract fun update(row: T): Int

    @Update
    abstract fun update(vararg entities: T): Int

    @Delete
    abstract fun delete(vararg entities: T): Int

    fun insert(entity: T): Long {
        var parentPath = PATH_DELIMITER  // Default path defines a root node
        var parentDepth = -1

        if (entity.hasParent()) {
            val parent = internalGet(String.format(GET, entity.parent))
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
        return getDescendantIds(get(id).path)
    }

    fun getAncestorIds(id: Long): List<Long> {
        return getAncestorIds(get(id).path)
    }

    fun getDescendants(id: Long): List<T> {
        return getDescendants(get(id).path)
    }

    fun getAncestors(id: Long): List<T> {
        return getAncestors(get(id).path)
    }

    fun getDescendantIds(path: String): List<Long> {
        return internalGetDescendantIds(String.format(DESCENDANT_ID, path))
    }

    fun getAncestorIds(path: String): List<Long> {
        return internalGetAncestorIds(String.format(ANCESTOR_ID, path))
    }

    fun getDescendants(path: String): List<T> {
        return internalGetDescendants(String.format(DESCENDANT_ENTITY, path))
    }

    fun getAncestors(path: String): List<T> {
        return internalGetAncestors(String.format(ANCESTOR_ENTITY, path))
    }
}
