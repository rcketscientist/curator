package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;

import com.anthonymandra.content.Meta;

import static android.arch.persistence.room.ForeignKey.CASCADE;


@Entity(tableName = Meta.META,
		foreignKeys = @ForeignKey(entity = MetadataEntity.class,
				parentColumns = FolderEntity._ID,
				childColumns = Meta.PARENT,
				onDelete = CASCADE),
		indices = { @Index(value = Meta.URI, unique = true),
					@Index(value = Meta.DOCUMENT_ID, unique = true),
					@Index(value = Meta.PARENT)})
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
	@ColumnInfo(name = Meta._ID)
	public Integer id;

	@ColumnInfo(name = Meta.NAME)
	public String name;

	@ColumnInfo(name = Meta.TYPE)
	public int type;

	@ColumnInfo(name = Meta.PROCESSED)
	public Boolean processed;

	@ColumnInfo(name = Meta.URI)
	public String uri;

	@ColumnInfo(name = Meta.DOCUMENT_ID)
	public String documentId;

	@ColumnInfo(name = Meta.PARENT)
	public long parent;

	@ColumnInfo(name = Meta.RATING)
	public String rating;

//	Subject is defined by junction table
//	public List<String> subject;

	@ColumnInfo(name = Meta.LABEL)  //table
	public String label;

	@ColumnInfo(name = Meta.TIMESTAMP)
	public String timestamp;

	@ColumnInfo(name = Meta.MAKE)
	public String make;

	@ColumnInfo(name = Meta.MODEL)  // Make and Model tables, only need model, make is implicit http://en.tekstenuitleg.net/articles/software/database-design-tutorial/second-normal-form.html
	public String model;

	@ColumnInfo(name = Meta.APERTURE)
	public String aperture;

	@ColumnInfo(name = Meta.EXPOSURE)
	public String exposure;

	@ColumnInfo(name = Meta.FLASH)
	public String flash;

	@ColumnInfo(name = Meta.FOCAL_LENGTH)
	public String focalLength;

	@ColumnInfo(name = Meta.ISO)
	public String iso;

	@ColumnInfo(name = Meta.WHITE_BALANCE)
	public String whiteBalance;

	@ColumnInfo(name = Meta.HEIGHT)
	public int height;

	@ColumnInfo(name = Meta.WIDTH)
	public int width;

	@ColumnInfo(name = Meta.LATITUDE)
	public String latitude;

	@ColumnInfo(name = Meta.LONGITUDE)
	public String longitude;

	@ColumnInfo(name = Meta.ALTITUDE)
	public String altitude;

	@ColumnInfo(name = Meta.ORIENTATION)
	public int orientation;

	@ColumnInfo(name = Meta.LENS_MODEL)//table
	public String lens;

	@ColumnInfo(name = Meta.DRIVE_MODE)//table
	public String driveMode;

	@ColumnInfo(name = Meta.EXPOSURE_MODE)//table
	public String exposureMode;

	@ColumnInfo(name = Meta.EXPOSURE_PROGRAM)//table
	public String exposureProgram;
}

