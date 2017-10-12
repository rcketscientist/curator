package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.ColumnInfo;

import com.anthonymandra.content.Meta;

public class UriId
{
	public static final String SELECT = Meta.URI + "," + Meta._ID;

	@ColumnInfo(name = Meta.URI)
	public String uri;

	@ColumnInfo(name = Meta._ID)
	public long id;
}
