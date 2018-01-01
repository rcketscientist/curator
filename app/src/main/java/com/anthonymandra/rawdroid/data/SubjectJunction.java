package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.Index;
import android.support.annotation.NonNull;

import static android.arch.persistence.room.ForeignKey.CASCADE;

@Entity(
	tableName = "meta_subject_junction",
	primaryKeys = {"metaId", "subjectId"},
	indices = {
		@Index(value = "subjectId"),
		@Index(value = "metaId")},
	foreignKeys = {
		@ForeignKey(
			entity = MetadataEntity.class,
			parentColumns = "id",
			childColumns = "metaId",
			onDelete = CASCADE),
		@ForeignKey(
			entity = SubjectEntity.class,
			parentColumns = "id",
			childColumns = "subjectId",
			onDelete = CASCADE)})
public class SubjectJunction {
	public SubjectJunction(@NonNull Long metaId, @NonNull Long subjectId) {
		this.metaId = metaId;
		this.subjectId = subjectId;
	}

	@NonNull
	public Long metaId;

	@NonNull
	public Long subjectId;
}
