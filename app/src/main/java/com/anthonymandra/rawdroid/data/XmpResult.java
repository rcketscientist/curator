package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.ColumnInfo;

import com.anthonymandra.content.Meta;

public class XmpResult extends Xmp
{
	public static final String SELECT = Xmp.SELECT + ", " + Meta.URI;

	@ColumnInfo(name = Meta.URI)
	public String uri;
}
