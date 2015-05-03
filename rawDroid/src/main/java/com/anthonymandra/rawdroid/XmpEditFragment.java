package com.anthonymandra.rawdroid;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Toast;

import com.android.gallery3d.common.Utils;
import com.anthonymandra.content.Meta;
import com.anthonymandra.framework.ImageUtils;
import com.drew.metadata.Metadata;
import com.drew.metadata.xmp.XmpDirectory;
import com.drew.metadata.xmp.XmpWriter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class XmpEditFragment extends XmpBaseFragment
{
	private static final String TAG = XmpEditFragment.class.getSimpleName();
	public static final String FRAGMENT_TAG = "XmpEditFragment";

	Uri currentUri;

	XmpEditValues lastWrittenXmp = new XmpEditValues();
	XmpEditValues originalXmp;

	private boolean hasWritten = false;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.xmp_edit_landscape, container, false);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		attachButtons();
	}

	@Override
	public void onStop()
	{
		super.onStop();
		writeCurrentXmp();
	}

	public void setMediaObject(Cursor image)
	{
		if (originalXmp != null)
			writeCurrentXmp();
		clear();
		originalXmp = parseXmp(image);
		currentUri = Uri.parse(image.getString(Meta.URI_COLUMN));
		setXmp(originalXmp);
	}

	private void writeCurrentXmp()
	{
		// Avoid writing blank xmp on already empty xmp, but
		// Allow writing blank when cleared
		boolean hasModifications = hasModifications();
		if (!hasModifications)
		{
			return;
		}

		lastWrittenXmp.Label = getLabel();
		lastWrittenXmp.Rating = getRating();
		lastWrittenXmp.Subject = getSubject();
		hasWritten = true;

		boolean success = updateContent(lastWrittenXmp);

        try
        {
			writeXmp();
        }
        catch (FileNotFoundException e)
        {
            Toast.makeText(getActivity(), "XMP file could not be created.  Google disabled write access in Android 4.4+.  You can root to fix, or use a card reader.", Toast.LENGTH_LONG).show();
        }
    }

	public void writeXmp() throws FileNotFoundException
	{
		File xmp = ImageUtils.getXmpFile(new File(currentUri.getPath()));
		final OutputStream os = new BufferedOutputStream(
				new FileOutputStream(
						ImageUtils.getXmpFile(xmp)
				));
		try
		{
			final Metadata meta = new Metadata();
			meta.addDirectory(new XmpDirectory());
			updateSubject(meta, lastWrittenXmp.Subject);
			updateRating(meta, lastWrittenXmp.Rating);
			updateLabel(meta, lastWrittenXmp.Label);

			new Thread(new Runnable()
			{
				@Override
				public void run()
				{
					if (meta.containsDirectoryOfType(XmpDirectory.class))
						XmpWriter.write(os, meta);
				}
			}).start();
		}
		finally
		{
			Utils.closeSilently(os);
		}
	}

	public static void updateRating(Metadata meta, Integer rating)
    {
        XmpDirectory xmp = meta.getFirstDirectoryOfType(XmpDirectory.class);
        if (rating == null)
        {
            if (xmp != null)
                xmp.deleteProperty(XmpDirectory.TAG_RATING);
        }
        else
		{
			xmp.updateDouble(XmpDirectory.TAG_RATING, rating);
        }
    }

    public static void updateLabel(Metadata meta, String label)
    {
        XmpDirectory xmp = meta.getFirstDirectoryOfType(XmpDirectory.class);
        if (label == null)
        {
            if (xmp != null)
                xmp.deleteProperty(XmpDirectory.TAG_LABEL);

        }
        else
		{
			xmp.updateString(XmpDirectory.TAG_LABEL, label);
        }
    }

    public static void updateSubject(Metadata meta, String[] subject)
    {
        XmpDirectory xmp = meta.getFirstDirectoryOfType(XmpDirectory.class);
        if (subject == null)
        {
            if (xmp != null)
                xmp.deleteProperty(XmpDirectory.TAG_SUBJECT);
        }
        else
        {
            xmp.updateStringArray(XmpDirectory.TAG_SUBJECT, subject);
        }
    }

	private XmpEditValues parseXmp(Cursor c)
	{
		if (c == null)
		{
			return null;
		}

//		Cursor c = null;
//		try
//		{
//			c = getActivity().getContentResolver().query(
//					Meta.Data.CONTENT_URI,
//					null,
//					Meta.Data.URI + " = ?",
//					new String[]{currentUri.toString()},
//					null);
//
//			if (c == null)
//			{
//				return null;
//			}

			XmpEditValues xmp = new XmpEditValues();
//			c.moveToFirst();    // Can only be one result
			xmp.Label = c.getString(Meta.LABEL_COLUMN);
			xmp.Subject = ImageUtils.convertStringToArray(c.getString(Meta.SUBJECT_COLUMN));
			double rating = c.getDouble(Meta.RATING_COLUMN);
			xmp.Rating = Double.isNaN(rating) ? null : (int)rating;
			return xmp;
//		}
//		finally
//		{
//			Utils.closeSilently(c);
//		}
	}

	/**
	 * Update the data source
	 * @param xmp
	 * @return
	 */
	private boolean updateContent(XmpEditValues xmp)
	{
		ContentValues cv = new ContentValues();
		cv.put(Meta.Data.LABEL, xmp.Label);
		cv.put(Meta.Data.RATING, xmp.Rating);
		cv.put(Meta.Data.SUBJECT, ImageUtils.convertArrayToString(xmp.Subject));

		return getActivity().getContentResolver().update(
				Meta.Data.CONTENT_URI,
				cv, Meta.Data.URI + " = ?",
				new String[]{currentUri.toString()}) > 0;
	}

	private void clearXmp()
	{
		setXmp(new XmpEditValues());
		clear();
	}

	/**
	 * Convenience method for single select XmpLabelGroup
	 * @return
	 */
	private String getLabel()
	{
		String[] label = getColorLabels();
		return label != null ? label[0] : null;
	}

	protected void setColorLabel(String label)
	{
		if (label == null)
			setColorLabel((String[]) null);
		else
			setColorLabel(new String[]{label});
	}

	/**
	 * Convenience method for single select XmpLabelGroup
	 * @return
	 */
	private Integer getRating()
	{
		Integer[] ratings = getRatings();
		return ratings != null ? ratings[0] : null;
	}

	protected void setRating(Integer rating)
	{
		if (rating == null)
			setRating((Integer[]) null);
		else
			setRating(new Integer[]{rating});
	}

	protected void setXmp(XmpEditValues xmp)
	{
		setColorLabel(xmp.Label);
		setSubject(xmp.Subject);
		setRating(xmp.Rating);
	}

	private boolean hasModifications()
	{
		Integer widgetRating = getRating();
		String widgetLabel = getLabel();
		String[] widgetSubject = getSubject();

		boolean bothLabelNull = widgetLabel == null && originalXmp.Label == null;
		boolean bothRatingNull = widgetRating == null && originalXmp.Rating == null;
		boolean bothSubjectNull = widgetSubject == null && originalXmp.Subject == null;

		if (!bothRatingNull && widgetRating != originalXmp.Rating)
		{
			if (widgetRating == null)
				return true;
			if (!widgetRating.equals(originalXmp.Rating))
				return true;
		}
		if (!bothLabelNull)
		{
			if (widgetLabel == null)
				return true;
			if (!widgetLabel.equals(originalXmp.Label))
				return true;
		}
		if (!bothSubjectNull)
		{
			if (widgetSubject == null)
				return true;
			if (!Arrays.equals(widgetSubject, originalXmp.Subject))
				return true;
		}
		return false;
//        return !bothSubjectNull && !Arrays.equals(widgetSubject, originalXmp.Subject);
    }

	private void attachButtons()
	{
		getActivity().findViewById(R.id.buttonReuseXmp).setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				if (hasWritten)
				{
					setXmp(lastWrittenXmp);
				}
			}
		});

		getActivity().findViewById(R.id.clearButton).setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				clearXmp();
			}
		});
	}

	/**
	 * Default values indicate no xmp
	 */
	private class XmpEditValues
	{
		Integer Rating = null;
		String[] Subject = null;
		String Label = null;
	}
}
