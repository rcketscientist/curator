package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.RawQuery;
import android.arch.persistence.room.Update;

import java.util.List;

public abstract class PathDao<T extends PathEntity>
{
    protected abstract String getDatabase();
    private final String PATH_DELIMITER = "/";
    private final String SELECT_ID = "SELECT id ";
    private final String SELECT_ALL = "SELECT * ";
    private final String FROM = "FROM " + getDatabase();
    private final String GET =                  "SELECT * " + FROM + " WHERE id = %s ";
    private final String ANCESTOR_ID =          SELECT_ID + FROM + " WHERE '%s' LIKE path || '%%' ";
    private final String DESCENDANT_ID =        SELECT_ID + FROM + " WHERE path LIKE '%s' || '%%' ";
    private final String ANCESTOR_ENTITY =      SELECT_ALL + FROM + " WHERE '%s' LIKE path || '%%' ";
    private final String DESCENDANT_ENTITY =    SELECT_ALL + FROM + " WHERE path LIKE '%s' || '%%' ";

    @RawQuery
    protected abstract T internalGet(String query);

    @RawQuery
    protected abstract List<Long> internalGetAncestorIds(String query);

    @RawQuery
    protected abstract List<Long> internalGetDescendantIds(String query);

    @RawQuery
    protected abstract List<T> internalGetAncestors(String query);

    @RawQuery
    protected abstract List<T> internalGetDescendants(String query);

    public T get(Long id) {
        return internalGet(String.format(GET, id));
    }

    @Insert
    protected abstract Long insertInternal(T row);

    @Update
    public abstract int update(T row);

    @Update
    public abstract int update(T... entities);

    @Delete
    public abstract int delete(T... entities);

    public long insert(T entity)
    {
        String parentPath = PATH_DELIMITER;  // Default path defines a root node
        int parentDepth = -1;

        if (entity.hasParent())
        {
            PathEntity parent = internalGet(String.format(GET, entity.getParent()));
            parentPath = parent.getPath() + PATH_DELIMITER;
            parentDepth = parent.getDepth();
        }

        // Would we want path to be unique?
        Long childId = insertInternal(entity);
        if (childId == -1)
            return -1;

        // Update the child entry with its full path
        entity.setId(childId);
        entity.setPath(parentPath + childId);
        entity.setDepth(parentDepth + 1);

        update(entity);
        return childId;
    }

    public List<Long> getDescendantIds(long id) {
        return getDescendantIds(get(id).getPath());
    }

    public List<Long> getAncestorIds(long id) {
        return getAncestorIds(get(id).getPath());
    }

    public List<T> getDescendants(long id) {
        return getDescendants(get(id).getPath());
    }

    public List<T> getAncestors(long id) {
        return getAncestors(get(id).getPath());
    }

    public List<Long> getDescendantIds(String path) {
        return internalGetDescendantIds(String.format(DESCENDANT_ID, path));
    }

    public List<Long> getAncestorIds(String path) {
        return internalGetAncestorIds(String.format(ANCESTOR_ID, path));
    }

    public List<T> getDescendants(String path) {
        return internalGetDescendants(String.format(DESCENDANT_ENTITY, path));
    }

    public List<T> getAncestors(String path) {
        return internalGetAncestors(String.format(ANCESTOR_ENTITY, path));
    }
}
