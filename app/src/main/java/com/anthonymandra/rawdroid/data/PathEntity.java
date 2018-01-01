package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

@Entity
public abstract class PathEntity
{
	PathEntity() {}
	PathEntity(String path, int depth, Long parent)
	{
		this.path = path;
		this.depth = depth;
		this.parent = parent;
	}

	PathEntity(Long id, String path, int depth, Long parent)
	{
		this.id = id;
		this.path = path;
		this.depth = depth;
		this.parent = parent;
	}

	/**
	 * Primary key
	 */
	@PrimaryKey(autoGenerate = true)
	public Long id;

	/**
	 * Path identifying the hierarchy of data
	 */
	public String path = "";

	/**
	 * This item's parent id or null if it is a root
	 */
	public Long parent = null;

	/**
	 * The depth at which this item resides (number of parents)
	 */
	public int depth;

	@Override
	public boolean equals(Object obj)
	{
		if (obj instanceof PathEntity)
		{
			PathEntity compare = (PathEntity) obj;
			return  id.equals(compare.id) &&
					path.equals(compare.path) &&
					depth == compare.depth;
		}
		return false;
	}
}
