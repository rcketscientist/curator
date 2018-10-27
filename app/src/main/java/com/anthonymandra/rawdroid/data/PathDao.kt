package com.anthonymandra.rawdroid.data

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.RawQuery
import androidx.room.Update

private const val PATH_DELIMITER = "/"

abstract class PathDao<T : PathEntity>(database: String) {
    private val from = "from $database"
    private val getQuery = "SELECT * $from WHERE id = %s "
    private val ancestorIdQuery = "SELECT id $from WHERE '%s' LIKE path || '%%' "
    private val descendantIdQuery = "SELECT id $from WHERE path LIKE '%s' || '%%' "
    private val ancestorQuery = "SELECT * $from WHERE '%s' LIKE path || '%%' "
    private val descendantQuery = "SELECT * $from WHERE path LIKE '%s' || '%%' "

    @RawQuery
    protected abstract fun internalGet(query: SupportSQLiteQuery): T

    @RawQuery
    protected abstract fun internalGetAncestorIds(query: SupportSQLiteQuery): List<Long>

    @RawQuery
    protected abstract fun internalGetDescendantIds(query: SupportSQLiteQuery): List<Long>

    @RawQuery
    protected abstract fun internalGetAncestors(query: SupportSQLiteQuery): List<T>

    @RawQuery
    protected abstract fun internalGetDescendants(query: SupportSQLiteQuery): List<T>

    operator fun get(id: Long?): T {
        return internalGet(SimpleSQLiteQuery(String.format(getQuery, id)))
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
            val parent = internalGet(SimpleSQLiteQuery(String.format(getQuery, entity.parent)))
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

    private fun getDescendantIds(path: String): List<Long> {
        return internalGetDescendantIds(SimpleSQLiteQuery(String.format(descendantIdQuery, path)))
    }

    private fun getAncestorIds(path: String): List<Long> {
        return internalGetAncestorIds(SimpleSQLiteQuery(String.format(ancestorIdQuery, path)))
    }

    fun getDescendants(path: String): List<T> {
        return internalGetDescendants(SimpleSQLiteQuery(String.format(descendantQuery, path)))
    }

    fun getAncestors(path: String): List<T> {
        return internalGetAncestors(SimpleSQLiteQuery(String.format(ancestorQuery, path)))
    }
}
