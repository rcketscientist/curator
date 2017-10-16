package com.anthonymandra.rawdroid.data.result;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Embedded;

import com.anthonymandra.content.Meta;
import com.anthonymandra.rawdroid.data.Xmp;

public class GalleryResult
{
	public static final String SELECT = Xmp.SELECT + ", " +
		Meta._ID + ", " + Meta.URI + ", " + Meta.NAME + ", " + Meta.TIMESTAMP + ", " +
		Meta.ORIENTATION + ", " + Meta.TYPE + ", " + Meta.PARENT;

	@ColumnInfo(name = Meta._ID)
	public long id;

	@ColumnInfo(name = Meta.URI)
	public String uri;

	@ColumnInfo(name = Meta.NAME)
	public String name;

	@Embedded
	public Xmp xmp;

	@ColumnInfo(name = Meta.TIMESTAMP)
	public String timestamp;

	@ColumnInfo(name = Meta.ORIENTATION)
	public int orientation;

	@ColumnInfo(name = Meta.TYPE)
	public int type;

	@ColumnInfo(name = Meta.PARENT)
	public String parent;
}
