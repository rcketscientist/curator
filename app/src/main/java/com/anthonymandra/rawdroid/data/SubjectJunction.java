package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;

import com.anthonymandra.content.Meta;

import static android.arch.persistence.room.ForeignKey.CASCADE;

@Entity(primaryKeys = {SubjectJunction.META_ID, SubjectJunction.SUBJECT_ID},
	indices = {
		@Index(value = SubjectJunction.SUBJECT_ID),
		@Index(value = SubjectJunction.META_ID)},
	foreignKeys = {
		@ForeignKey(
			entity = MetadataEntity.class,
			parentColumns = Meta._ID,
			childColumns = SubjectJunction.META_ID,
			onDelete = CASCADE),
		@ForeignKey(
			entity = SubjectEntity.class,
			parentColumns = SubjectEntity._ID,
			childColumns = SubjectJunction.SUBJECT_ID,
			onDelete = CASCADE)})
public class SubjectJunction
{
	public static final String META_ID = "meta_id";
	public static final String SUBJECT_ID = "subject_id";
	public static final String _ID = BaseColumns._ID;

	@ColumnInfo(name = META_ID)
	@NonNull
	public Long metaId;

	@ColumnInfo(name = SUBJECT_ID)
	@NonNull
	public Long subjectId;
}
