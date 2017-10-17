package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.provider.BaseColumns;

@Entity(foreignKeys =
		@ForeignKey(entity = SubjectEntity.class,
					parentColumns = SubjectEntity._ID,
					childColumns = SynonymEntity.SUBJECT_ID))
public class SynonymEntity
{
	public static final String _ID = BaseColumns._ID;
	public static final String SUBJECT_ID = "subject_id";
	public static final String SYNONYM = "synonym";

	@ColumnInfo(name = _ID)
	public long id;

	@ColumnInfo(name = SUBJECT_ID)
	public long subjectId;

	@ColumnInfo(name = SYNONYM)
	public String synonym;
}
