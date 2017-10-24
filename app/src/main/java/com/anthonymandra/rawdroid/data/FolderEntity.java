package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;

@Entity(tableName = FolderEntity.DATABASE)
public class FolderEntity extends PathEntity
{
	public static final String DATABASE = "image_parent";
	public static final String DOCUMENT_ID = "document_id";
	public static final String NAME = "name";

	FolderEntity(String documentId, long id, String path, int depth)
	{
		super(id, path, depth);
		this.documentId = documentId;
	}

	@ColumnInfo(name = DOCUMENT_ID)
	public String documentId;   // TODO: This might not be necessary, prolly should have uri though

	@Override
	public boolean equals(Object obj)
	{
		if (obj instanceof FolderEntity)
		{
			FolderEntity compare = (FolderEntity) obj;
			return super.equals(obj) && documentId.equals(compare.documentId);
		}
		return false;
	}
}
