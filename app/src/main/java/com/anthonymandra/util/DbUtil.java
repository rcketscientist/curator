package com.anthonymandra.util;

import com.drew.lang.annotations.Nullable;

import java.util.List;

public class DbUtil
{
	private static final String FIELD_SEPARATOR = "__,__";

	/***
	 * Creates a LIKE selection statement with all of the given arguments
	 * @param column column to select on
	 * @param likes array of arguments to select on
	 * @param selectionArgs out: formatted arguments to pass to query
	 * @param joiner joiner between individual LIKE
	 * @param NOT true to set NOT LIKE for all selection arguments
	 * @param argStart set a wildcard before every selection argument
	 * @param argEnd set a wildcard after every selection argument
	 * @return selection statement to query
	 */
	public static String createLike(String column, String[] likes, List<String> selectionArgs,
	                                String joiner, boolean NOT,
	                                @Nullable String argStart, @Nullable String argEnd,
	                                @Nullable String escapeChar)
	{
		StringBuilder selection = new StringBuilder();
		for (int i = 0; i < likes.length; i++)
		{
			if (i > 0) selection.append(joiner);

			if (argStart == null)
				argStart = "";
			if (argEnd == null)
				argEnd = "";

			selection.append(column)
					.append(NOT ? " NOT LIKE ?" : " LIKE ?");

			if (escapeChar != null)
				selection.append(" ESCAPE '\\'");

			String argument = likes[i];
			if (escapeChar != null)
				argument = argument.replace(escapeChar, "\\" + escapeChar);
			argument = argStart + argument + argEnd;
			selectionArgs.add(argument);
		}

		return selection.toString();
	}

	public static String createIN(String column, int arguments)
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
