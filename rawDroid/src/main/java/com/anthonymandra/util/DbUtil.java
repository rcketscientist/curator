package com.anthonymandra.util;

import java.util.List;

public class DbUtil
{
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
		StringBuilder selection = new StringBuilder();
		selection.append(column)
				.append(" IN (")
				.append(makePlaceholders(arguments))
				.append(")");
		return selection.toString();
	}

	public static String makePlaceholders(int len) {
		StringBuilder sb = new StringBuilder(len * 2 - 1);
		sb.append("?");
		for (int i = 1; i < len; i++)
			sb.append(",?");
		return sb.toString();
	}
}
