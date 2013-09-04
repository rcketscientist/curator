package com.anthonymandra.rawdroid.beta;

import android.content.Context;
import android.database.Cursor;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class RawCursorAdapter extends SimpleCursorAdapter
{

	public RawCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to)
	{
		super(context, layout, c, from, to);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void setViewImage(ImageView v, String value)
	{
		// TODO Auto-generated method stub
		super.setViewImage(v, value);
	}

	@Override
	public void setViewText(TextView v, String text)
	{
		// TODO Auto-generated method stub
		super.setViewText(v, text);
	}

}
