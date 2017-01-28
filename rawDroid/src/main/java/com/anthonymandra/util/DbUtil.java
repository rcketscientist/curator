package com.anthonymandra.util;

import java.util.List;

public class DbUtil
{
	private static final String FIELD_SEPARATOR = "__,__";

	public static String createMultipleLike(String column, String[] likes, List<String> selectionArgs, String joiner, boolean NOT)
	{
		StringBuilder selection = new StringBuilder();
		for (int i = 0; i < likes.length; i++)
		{
			if (i > 0) selection.append(joiner);
			selection.append(column)
					.append(NOT ? " NOT LIKE ?" : " LIKE ?");
			selectionArgs.add("%" + likes[i] + "%");
		}

		return selection.toString();
	}

	public static String createMultipleIN(String column, int arguments)
	{
		return column +
				" IN (" +
				makePlaceholders(arguments) +
				")";
	}

	private static String makePlaceholders(int len) {
		StringBuilder sb = new StringBuilder(len * 2 - 1);
		sb.append("?");
		for (int i = 1; i < len; i++)
			sb.append(",?");
		return sb.toString();
	}

	public static String convertArrayToString(String[] array){
	    if (array == null)
	        return null;
	    String str = "";
	    for (int i = 0;i<array.length; i++) {
	        str = str+array[i];
	        // Do not append comma at the end of last element
	        if(i<array.length-1){
	            str = str+ FIELD_SEPARATOR;
	        }
	    }
	    return str;
	}

	public static String[] convertStringToArray(String str){
	    if (str == null)
	        return null;
		return str.split(FIELD_SEPARATOR);
	}
}
