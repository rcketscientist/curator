package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;

import static android.arch.persistence.room.ForeignKey.CASCADE;


@Entity(tableName = "meta",
		foreignKeys = @ForeignKey(entity = FolderEntity.class,
				parentColumns = "id",
				childColumns = "parentId",
				onDelete = CASCADE),
		indices = { @Index(value = "uri", unique = true),
					@Index(value = "documentId", unique = true),
					@Index(value = "parentId")})
public class MetadataEntity
{
	public static MetadataEntity createMetadataEntity(SearchEntity file) {
		MetadataEntity meta = new MetadataEntity();
		meta.name = file.name;
		meta.type = file.type;
//		meta.parent = file.parent;  //TODO:
		meta.uri = file.uri;
		meta.documentId = file.documentId;
		meta.timestamp = file.timestamp;
		return meta;
	}

	@PrimaryKey(autoGenerate = true)
	public Long id;

	public String name;

	public int type;

	public Boolean processed;

	public String uri;

	// Unique documentId, we don't want duplicates from different root permissions
	public String documentId;

	public Long parentId;

	public String rating;

	public String label;

	public String timestamp;

	public String make;

	// Make and Model tables, only need model, make is implicit http://en.tekstenuitleg.net/articles/software/database-design-tutorial/second-normal-form.html
	public String model;

	public String aperture;

	public String exposure;

	public String flash;

	public String focalLength;

	public String iso;

	public String whiteBalance;

	public int height;

	public int width;

	public String latitude;

	public String longitude;

	public String altitude;

	public int orientation;

	public String lens;//todo: table

	public String driveMode;//todo: table

	public String exposureMode;//todo: table

	public String exposureProgram;//todo: table
}

