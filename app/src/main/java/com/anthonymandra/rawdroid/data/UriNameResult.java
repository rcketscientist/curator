package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.ColumnInfo;

import com.anthonymandra.content.Meta;

public class UriNameResult
{
	public static final String SELECT = Meta.URI + "," + Meta.NAME;

	@ColumnInfo(name = Meta.NAME)
	public String name;

	@ColumnInfo(name = Meta.URI)
	public String uri;
}
