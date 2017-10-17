package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Relation;

import com.anthonymandra.content.Meta;

public class Xmp
{
	public static final String SELECT = Meta.RATING + ", " + Meta.SUBJECT + ", " + Meta.LABEL;

	@ColumnInfo(name = Meta.RATING)
	public String rating;

	@Relation(parentColumn = Meta._ID, entityColumn = Meta.SUBJECT)
	public String subject;

	@ColumnInfo(name = Meta.LABEL)
	public String label;

}
