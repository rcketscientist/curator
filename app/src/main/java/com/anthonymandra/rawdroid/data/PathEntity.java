package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.provider.BaseColumns;

@Entity
public abstract class PathEntity
{
	public static final String _ID = BaseColumns._ID;
	public static final String PATH = "path";
	public static final String DEPTH = "depth";
	public static final String PARENT = "parent";

	PathEntity(long id, String path, int depth, Long parent)
	{
		this.id = id;
		this.path = path;
		this.depth = depth;
		this.parent = parent;
	}

	@PrimaryKey(autoGenerate = true)
	@ColumnInfo(name = _ID)
	public Long id;

	@ColumnInfo(name = PATH)
	public String path;

	public Long parent = null;

	@ColumnInfo(name = DEPTH)
	public int depth;

	@Override
	public boolean equals(Object obj)
	{
		if (obj instanceof FolderEntity)
		{
			FolderEntity compare = (FolderEntity) obj;
			return  id.equals(compare.id) &&
					path.equals(compare.path) &&
					depth == compare.depth;
		}
		return false;
	}
}
