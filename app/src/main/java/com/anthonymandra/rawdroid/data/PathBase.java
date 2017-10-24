package com.anthonymandra.rawdroid.data;

import android.database.SQLException;

import java.util.List;

public abstract class PathBase<T extends PathEntity>
{
	private static final String PATH_DELIMITER = "/";

	abstract T getPath(Long id);
	abstract Long insertInternal(T row);
	abstract String getDatabase();

	/**
	 * Annotate the implementation with:<p>
	 * Query("SELECT " + PathEntity._ID + " FROM [DATABASE_NAME] WHERE :path LIKE " + PathEntity.PATH + " || '%'")
	 * @param path path to find
	 * @return ancestors of path
	 */
	public abstract List<Long> getAncestors(String path);

	/**
	 * Annotate the implementation with:<p>
	 * Query("SELECT " + PathEntity._ID + " FROM [DATABASE_NAME] " WHERE " + PathEntity.PATH + " LIKE :path || '%'")
	 * @param path path to find
	 * @return ancestors of path
	 */
	public abstract List<Long> getDescendants(String path);

	public long insert(T entity)
		{
		//TODO: Need to evaluate this old code
		String parentPath = PATH_DELIMITER;  // Default path defines a root node
		int parentDepth = -1;

		if (entity.parent > 0)
		{
			PathEntity parent = getPath(entity.parent);
			parentPath = parent.path + PATH_DELIMITER;
			parentDepth = parent.depth;
		}

		//FIXME: Do we need unique?
		// Since the column is unique we must put a unique placeholder
//		entity.path = UUID.randomUUID().toString();

		Long childId = insertInternal(entity);
		if (childId == -1)
			return -1;

		// Add the child id to the parent's path
		String childPath = parentPath + childId;
		// Update the child entry with its full path
		entity.path = childPath;    // TODO: Does this update?
		entity.depth = parentDepth + 1;

//		int rowsAffected = db.update(getTableName(), initialValues, getColumnId() + " = ?",
//				new String[] {Long.toString(childId)});

		throw new SQLException("Failed to insert row into " + getDatabase() + ": " + entity.path);
	}

	public List<Long> getDescendants(long id) {
		PathEntity pd = getPath(id);
		return getDescendants(pd.path);
	}

	public List<Long> getAncestors(long id) {
		PathEntity pd = getPath(id);
		return getAncestors(pd.path);
	}
}
