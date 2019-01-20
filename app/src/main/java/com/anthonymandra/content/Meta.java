package com.anthonymandra.content;

import android.net.Uri;
import android.provider.BaseColumns;
import android.util.SparseArray;

import com.drew.lang.annotations.Nullable;

import java.util.EnumSet;

public class Meta implements BaseColumns
{
	public enum ImageType
	{
		UNKNOWN (-1),
		UNPROCESSED (0),
		RAW (1),
		COMMON (2),
		TIFF (3);

		private static final SparseArray<ImageType > lookup = new SparseArray<>();
		static {
			for (ImageType type : EnumSet.allOf(ImageType.class))
				lookup.put(type.getValue(), type);
		}

		private int value;
		ImageType(int value) {this.value = value;}
		public static @Nullable ImageType fromInt(int n) { return lookup.get(n); }
		public int getValue() { return value; }
	}
}
