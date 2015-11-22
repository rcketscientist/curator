package com.anthonymandra.rawdroid;

import java.util.Set;

public class XmpFilter
{
	XmpValues xmp;
	boolean andTrueOrFalse;
	boolean sortAscending;
	boolean segregateByType;
	SortColumns sortColumn;
	Set<String> hiddenFolders;

	public enum SortColumns
	{
		Name,
		Date
	}
}
