package com.anthonymandra.framework;

import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;

import com.anthonymandra.content.Meta;
import com.anthonymandra.util.ImageUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

public class MetaDataCleaner
{
	private final static Semaphore threadLock = new Semaphore(1);

	/**
	 * Attempts to clean the metadata database.  Only one clean thread allowed at a time.
	 * @param c context
	 * @param h handler when clean is complete
	 * @return true if the clean was started, false if one is already running
	 */
	public static boolean cleanDatabase(final Context c, final Handler h)
	{
		if (!threadLock.tryAcquire())
			return false;

		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					final ArrayList<ContentProviderOperation> operations = new ArrayList<>();

					final String[] projection = new String[]{Meta.URI, Meta._ID};

					final List<String> images = new ArrayList<>();

					// Quickly convert the cursor
					try (Cursor cursor = c.getContentResolver().query(Meta.CONTENT_URI, projection, null, null, null))
					{
						if (cursor == null)
							return;

						final int uriColumn = cursor.getColumnIndex(Meta.URI);
						final int idColumn = cursor.getColumnIndex(Meta._ID);

						while (cursor.moveToNext())
						{
							String uriString = cursor.getString(uriColumn);
							if (uriString == null)  // we've got some bogus data, just remove
							{
								operations.add(ContentProviderOperation.newDelete(
										Uri.withAppendedPath(Meta.CONTENT_URI, cursor.getString(idColumn))).build());
								continue;
							}

							images.add(cursor.getString(uriColumn));
						}
					}

					for (String uriString : images)
					{
						Uri uri = Uri.parse(uriString);
						UsefulDocumentFile file = UsefulDocumentFile.fromUri(c, uri);
						if (!file.exists())
						{
							operations.add(ContentProviderOperation
									.newDelete(Meta.CONTENT_URI)
									.withSelection(ImageUtils.getWhereUri(), new String[]{uriString}).build());
						}
					}

					c.getContentResolver().applyBatch(Meta.AUTHORITY, operations);
					h.sendEmptyMessage(1);  //alert caller that clean is complete
				}
				catch (RemoteException | OperationApplicationException e)
				{
					e.printStackTrace();
				}
				finally
				{
					threadLock.release();
				}
			}
		}).start();

		return true;
	}
}
