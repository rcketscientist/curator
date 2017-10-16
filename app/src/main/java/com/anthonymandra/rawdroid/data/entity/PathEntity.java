package com.anthonymandra.rawdroid.data.entity;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.PrimaryKey;
import android.provider.BaseColumns;

public abstract class PathEntity
{
	public static final String _ID = BaseColumns._ID;
	public static final String PATH = "path";
	public static final String DEPTH = "depth";
	public static final String PARENT = "parent";

	@PrimaryKey
	@ColumnInfo(name = _ID)
	public long id;

	@ColumnInfo(name = PATH)
	public String path;

	public long parent = -1;

	@ColumnInfo(name = DEPTH)
	public int depth;
}
