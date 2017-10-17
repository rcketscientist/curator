package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import com.anthonymandra.content.Meta;

@Entity(foreignKeys = {
	@ForeignKey(entity = MetadataEntity.class,
				parentColumns = Meta._ID,
				childColumns = SubjectJunction.META_ID),
	@ForeignKey(entity = SubjectEntity.class,
				parentColumns = SubjectEntity._ID,
				childColumns = SubjectJunction.SUBJECT_ID)})
public class SubjectJunction
{
	public static final String META_ID = "meta_id";
	public static final String SUBJECT_ID = "subject_id";

	@ColumnInfo(name = META_ID)
	public String metaId;

	@ColumnInfo(name = SUBJECT_ID)
	public String subjectId;
}
