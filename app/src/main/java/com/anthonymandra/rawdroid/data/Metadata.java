package com.anthonymandra.rawdroid.data;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Embedded;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;

import com.anthonymandra.content.Meta;

import java.util.Arrays;
import java.util.List;


@Entity(tableName = Meta.META,
		indices = { @Index(value = Meta.URI, unique = true),
					@Index(value = Meta.DOCUMENT_ID, unique = true) })
public class Metadata
{
	@PrimaryKey(autoGenerate = true)
	@ColumnInfo(name = Meta._ID)
	public long id;

	@Embedded
	public FileInfo fileInfo;

	@Embedded
	public Xmp xmp;

	@ColumnInfo(name = Meta.TIMESTAMP)
	public String timestamp;

	@ColumnInfo(name = Meta.MAKE)
	public String make;

	@ColumnInfo(name = Meta.MODEL)
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

	@ColumnInfo(name = Meta.LENS_MODEL)
	public String lens;

	@ColumnInfo(name = Meta.DRIVE_MODE)
	public String driveMode;

	@ColumnInfo(name = Meta.EXPOSURE_MODE)
	public String exposureMode;

	@ColumnInfo(name = Meta.EXPOSURE_PROGRAM)
	public String exposureProgram;
}

class Xmp
{
	public static final String SELECT = Meta.RATING + ", " + Meta.SUBJECT + ", " + Meta.LABEL;

	@ColumnInfo(name = Meta.RATING)
	public String rating;

	//FIXME: Handled by routing table
//	@ColumnInfo(name = Meta.SUBJECT)
//	public String subject;

	@ColumnInfo(name = Meta.LABEL)
	public String label;
}

class FileInfo
{
	@ColumnInfo(name = Meta.NAME)
	public String name;

	@ColumnInfo(name = Meta.TYPE)
	public int type;

	@ColumnInfo(name = Meta.PROCESSED)
	public Boolean processed;

	@ColumnInfo(name = Meta.PARENT)
	public String parent;

	@ColumnInfo(name = Meta.URI)
	public String uri;
}

class UriName
{
	public static final String SELECT = Meta.URI + "," + Meta.NAME;

	@ColumnInfo(name = Meta.NAME)
	public String name;

	@ColumnInfo(name = Meta.URI)
	public String uri;
}