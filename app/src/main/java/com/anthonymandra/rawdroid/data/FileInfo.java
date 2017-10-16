package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.ColumnInfo;

import com.anthonymandra.content.Meta;

public class FileInfo
{
	@ColumnInfo(name = Meta.NAME)
	public String name;

	@ColumnInfo(name = Meta.TYPE)
	public int type;

	@ColumnInfo(name = Meta.PROCESSED)
	public Boolean processed;

	@ColumnInfo(name = Meta.PARENT)
	public String parent;

	@ColumnInfo(name = Meta.URI)
	public String uri;
}
