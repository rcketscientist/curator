package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Relation;
import android.provider.BaseColumns;

import com.anthonymandra.content.Meta;

import java.util.List;

public class Xmp
{
	public static final String SELECT = Meta.RATING + ", " + Meta.LABEL;

	@ColumnInfo(name = Meta.RATING)
	public String rating;

	@Relation(entity = SubjectJunction.class, parentColumn = BaseColumns._ID, entityColumn = SubjectJunction.META_ID)
	public List<SubjectJunction> subject;

	@ColumnInfo(name = Meta.LABEL)
	public String label;

}
