package com.anthonymandra.rawdroid.data;

import java.util.List;

public abstract class PathDao<T extends PathEntity>
{
	private static final String PATH_DELIMITER = "/";

	/**
	 * Annotate the implementation with:<p>
	 * Query("SELECT * FROM [DATABASE_NAME] WHERE id= :id ")
	 * @param id
	 * @return
	 */
	abstract T get(Long id);
	abstract Long insertInternal(T row);
	abstract void update(T row);

	/**
	 * Annotate the implementation with:<p>
	 * Query("SELECT id FROM [DATABASE_NAME] WHERE :path LIKE path || '%'")
	 * @param path path to find
	 * @return ancestors of path
	 */
	public abstract List<Long> getAncestorIds(String path);

	/**
	 * Annotate the implementation with:<p>
	 * Query("SELECT id FROM [DATABASE_NAME] WHERE path LIKE :path || '%'")
	 * @param path path to find
	 * @return ancestors of path
	 */
	public abstract List<Long> getDescendantIds(String path);

	public long insert(T entity)
		{
		String parentPath = PATH_DELIMITER;  // Default path defines a root node
		int parentDepth = -1;

		if (entity.parent != null)
		{
			PathEntity parent = get(entity.parent);
			parentPath = parent.path + PATH_DELIMITER;
			parentDepth = parent.depth;
		}

		// Would we want path to be unique?
		Long childId = insertInternal(entity);
		if (childId == -1)
			return -1;

		// Update the child entry with its full path
		entity.id = childId;
		entity.path = parentPath + childId;
		entity.depth = parentDepth + 1;

		update(entity);
		return childId;
	}

	public List<Long> getDescendantIds(long id) {
		PathEntity pd = get(id);
		return getDescendantIds(pd.path);
	}

	public List<Long> getAncestorIds(long id) {
		PathEntity pd = get(id);
		return getAncestorIds(pd.path);
	}
}
