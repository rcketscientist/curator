package com.anthonymandra.rawdroid;

import android.content.ContentValues;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Toast;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
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

	MediaItem mMedia;

	double lastRating;
	String[] lastSubject;
	String lastLabel;

	private boolean hasWritten = false;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		// Inflate the layout for this fragment
		View view;
		boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
		isPortrait = isPortrait && getResources().getBoolean(R.bool.hasTwoPanes) || !isPortrait && !getResources().getBoolean(R.bool.hasTwoPanes);// Single pane devices use the opposite bar full screen
		
		if (isPortrait)	// Single pane devices use the opposite bar full screen
		{
			view = inflater.inflate(R.layout.xmp_edit_portrait, container, false);
		}
		else
		{
			view = inflater.inflate(R.layout.xmp_edit_landscape, container, false);
		}
		
		return view;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		attachButtons();
		populateXmp();
	}

	public static XmpEditFragment newInstance(MediaItem media)
	{
		XmpEditFragment fragment = new XmpEditFragment();
		fragment.initialize(media);
		return fragment;
	}

	@Override
	public void onStop()
	{
		super.onStop();
		writeCurrentXmp();
	}

	private void initialize(MediaItem media)
	{
		mMedia = media;
	}

	public void setMediaObject(MediaItem media)
	{
		writeCurrentXmp();
		clear();
		initialize(media);
		populateXmp();
	}

	private void writeCurrentXmp()
	{
		// Obviously don't write if media doesn't exist
		if (mMedia == null)
		{
			return;
		}

		// Avoid writing blank xmp on already empty xmp, but
		// Allow writing blank when cleared
		if (!hasModifications())
		{
//			Log.d(TAG, "No modifications: " + mMedia.getName());
			return;			
		}

		lastLabel = getSingleLabel();
		lastRating = getRating();
		lastSubject = getSubject();
		hasWritten = true;

		updateContent();

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
		final OutputStream os = new BufferedOutputStream(
				new FileOutputStream(
						ImageUtils.getXmpFile(new File(mMedia.getFilePath()))
				));
		final Metadata meta = new Metadata();
		meta.addDirectory(new XmpDirectory());
		updateSubject(meta, lastSubject);
		updateRating(meta, lastRating);
		updateLabel(meta, lastLabel);

		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				if (meta.containsDirectoryOfType(XmpDirectory.class))
					XmpWriter.write(os, meta);
			}
		}).start();

		Utils.closeSilently(os);
	}

	public static void updateRating(Metadata meta, double rating)
    {
        XmpDirectory xmp = meta.getFirstDirectoryOfType(XmpDirectory.class);
        if (Double.isNaN(rating))
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
        if (subject.length == 0)
        {
            if (xmp != null)
                xmp.deleteProperty(XmpDirectory.TAG_SUBJECT);
        }
        else
        {
            xmp.updateStringArray(XmpDirectory.TAG_SUBJECT, subject);
        }
    }

	private XmpValues parseXmp()
	{
		if (mMedia == null)
		{
			return null;
		}

		Cursor c = getActivity().getContentResolver().query(Meta.Data.CONTENT_URI, null, Meta.Data.URI + " = ?", new String[]{mMedia.getUri().toString()}, null);
		if (c == null)
		{
			return null;
		}

		XmpValues xmp = new XmpValues();
		c.moveToFirst();	// Can only be one result
		xmp.label = new String[] {c.getString(Meta.LABEL_COLUMN)};
		xmp.subject = ImageUtils.convertStringToArray(c.getString(Meta.SUBJECT_COLUMN));
		xmp.rating = c.getDouble(Meta.RATING_COLUMN);
		return xmp;
	}

	private void updateContent()
	{
		ContentValues cv = new ContentValues();
		cv.put(Meta.Data.LABEL, lastLabel);
		cv.put(Meta.Data.RATING, lastRating);
		cv.put(Meta.Data.SUBJECT, ImageUtils.convertArrayToString(lastSubject));

		getActivity().getContentResolver().update(Meta.Data.CONTENT_URI, cv, Meta.Data.URI + " = ?", new String[]{mMedia.getUri().toString()});
	}

	private void clearXmp()
	{
		lastLabel = null;
		lastRating = Double.NaN;
		lastSubject = new String[0];

		updateContent();
	}

	/**
	 * Convenience method for single select XmpLabelGroup
	 * @return
	 */
	private String getSingleLabel()
	{
		String[] label = getColorLabel();
		return label != null ? label[0] : null;
	}

	private boolean hasModifications()
	{
		double widgetRating = getRating();
		String widgetLabel = getSingleLabel();
		String[] widgetSubject = getSubject();

		XmpValues xmp = parseXmp();

		boolean bothLabelNull = widgetLabel == null && xmp.label == null;
		boolean bothRatingNaN = Double.isNaN(widgetRating) && Double.isNaN(widgetRating);
		boolean bothSubjectEmpty = widgetSubject.length == 0 && xmp.subject == null;
		
		if (!bothRatingNaN && widgetRating != xmp.rating)
			return true;
		if (!bothLabelNull)
		{
			if (widgetLabel != null)
			{
				if (!widgetLabel.equals(xmp.label))
				{
					return true;
				}
			}
			else
			{
				return true;
			}
		}
        return !bothSubjectEmpty && !Arrays.equals(widgetSubject, xmp.subject);
    }

	private void populateXmp()
	{
		if (mMedia == null)
		{
			onDestroy();
			return;
		}

		XmpValues xmp = parseXmp();
		setXmp(xmp);
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
					clear();
					updateContent();
					populateXmp();
				}
			}
		});

		getActivity().findViewById(R.id.buttonClear).setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				clearXmp();
				clear();
			}
		});
	}
}
