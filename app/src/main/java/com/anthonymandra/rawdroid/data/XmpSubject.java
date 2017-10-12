package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.provider.BaseColumns;

@Entity
public class XmpSubject {
	public static final String _ID = BaseColumns._ID;
	public static final String NAME = "name";
	public static final String PATH = "path";
	public static final String DEPTH = "depth";
	public static final String RECENT = "recent";
	public static final String PARENT = "parent";

	@PrimaryKey(autoGenerate = true)
	@ColumnInfo(name = _ID)
	public long id;

	@ColumnInfo(name = NAME)
	public String name;

	@ColumnInfo(name = RECENT)
	public String recent;

	@ColumnInfo(name = DEPTH)
	public String depth;

	@ColumnInfo(name = PARENT)
	public String parent;

	@ColumnInfo(name = PATH)
	public String path;
}
