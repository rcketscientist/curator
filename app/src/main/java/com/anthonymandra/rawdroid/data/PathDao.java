package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;
import android.database.SQLException;

import java.util.List;

@Dao
public abstract class PathDao
{
	protected final static String DATABASE = "Overwrite";
	private static final String PATH_DELIMITER = "/";

	@Query("SELECT * FROM " + DATABASE + " WHERE " + PathEntity._ID + "= :id ")
	abstract PathEntity getPath(long id);    // private ideal

	@Query("SELECT " + PathEntity._ID + " FROM " + DATABASE +
			" WHERE " + PathEntity.PATH + " LIKE :path || '%'")
	abstract List<Long> getDescendantsInternal(String path);

	@Query("SELECT " + PathEntity._ID + " FROM " + DATABASE +
			" WHERE :path LIKE " + PathEntity.PATH + " || '%'")
	abstract List<Long> getAncestorsInternal(String path);

	@Insert
	abstract void insertInternal(PathEntity... entities);

//	@Insert
//	abstract List<Long> insertInternal(PathEntity... entities);

	@Update
	public abstract void update(PathEntity... entities);

	@Delete
	public abstract void delete(PathEntity... entities);

	public List<Long> getDescendants(long id) {
		PathEntity pd = getPath(id);
		return getDescendantsInternal(pd.path);
	}

	public List<Long> getAncestors(long id) {
		PathEntity pd = getPath(id);
		return getAncestorsInternal(pd.path);
	}

	public long insert(PathEntity entity)
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

		long childId = insertInternal(entity);
		if (childId == -1)
			return -1;

		// Add the child id to the parent's path
		String childPath = parentPath + childId;
		// Update the child entry with its full path
		entity.path = childPath;    // TODO: Does this update?
		entity.depth = parentDepth + 1;

//		int rowsAffected = db.update(getTableName(), initialValues, getColumnId() + " = ?",
//				new String[] {Long.toString(childId)});

		throw new SQLException("Failed to insert row into " + DATABASE + ": " + entity.path);
	}


}
