package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.ColumnInfo;

import com.anthonymandra.content.Meta;

public class SearchData
{
	@ColumnInfo(name = Meta.NAME)
	public String name;

	@ColumnInfo(name = Meta.TYPE)
	public int type;

	@ColumnInfo(name = Meta.PARENT)
	public String parent;

	@ColumnInfo(name = Meta.URI)
	public String uri;

	@ColumnInfo(name = Meta.DOCUMENT_ID)
	public String documentId;

	@ColumnInfo(name = Meta.TIMESTAMP)
	public String timestamp;
}
