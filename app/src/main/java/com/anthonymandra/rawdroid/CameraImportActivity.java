package com.anthonymandra.rawdroid;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.mtp.MtpDevice;
import android.mtp.MtpObjectInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import com.anthonymandra.content.Meta;
import com.anthonymandra.framework.AsyncTask;
import com.anthonymandra.framework.DocumentActivity;
import com.anthonymandra.framework.SearchService;
import com.anthonymandra.framework.UsefulDocumentFile;
import com.anthonymandra.util.ImageUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
public class CameraImportActivity extends DocumentActivity
{
	private static final int REQUEST_MTP_IMPORT_DIR = 3;

	private enum WriteActions
	{
		IMPORT
	}

	private MtpDevice mMtpDevice;
	UsefulDocumentFile destination;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		requestImportImageLocation();
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		if (mMtpDevice != null)
			mMtpDevice.close();
	}

	@TargetApi(12)
	private void getMtpDevice()
	{
		UsbDevice device = getIntent().getParcelableExtra(UsbManager.EXTRA_DEVICE);
		if (device != null)
		{
			UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
			if (usbManager == null)
			{
				Toast.makeText(this, "USB Error 01: Failed to access bus.", Toast.LENGTH_SHORT).show();
				finish();
			}
			UsbDeviceConnection usbDeviceConnection = usbManager.openDevice(device);
			if (usbDeviceConnection == null)
			{
				Toast.makeText(this, "USB Error 02: Failed to open device.", Toast.LENGTH_SHORT).show();
                finish();
			}
			mMtpDevice = new MtpDevice(device);
			if (!mMtpDevice.open(usbDeviceConnection))
            {
                Toast.makeText(this, "USB Error 03: Failed to create connection.", Toast.LENGTH_SHORT).show();
                finish();
            }
		}
	}

	private List<Integer> getImageFiles()
	{
		int[] storageIds = mMtpDevice.getStorageIds();
		if (storageIds == null)
		{
			return null;
		}

		List<Integer> imageHandles = new ArrayList<>();
		for (int storageId : storageIds)
		{
			int[] handles = mMtpDevice.getObjectHandles(storageId, 0, 0);
			if (handles == null)
				continue;

			for (int objectId : mMtpDevice.getObjectHandles(storageId, 0, 0))
			{
				MtpObjectInfo info = mMtpDevice.getObjectInfo(objectId);
				if (info == null)
					continue;
				// TODO: Is this sufficient?
				if (info.getImagePixHeight() > 0)// info.getAssociationType() != MtpConstants.ASSOCIATION_TYPE_GENERIC_FOLDER)
					imageHandles.add(objectId);
			}
		}
        return imageHandles;
	}

	protected class ImportTask extends AsyncTask<Void, Void, Boolean> implements OnCancelListener
	{
		private boolean cancelled;
		private ProgressDialog mProgressDialog;

		final ArrayList<ContentValues> dbInserts = new ArrayList<>();

		@Override
		protected void onPreExecute()
		{
			mProgressDialog = new ProgressDialog(CameraImportActivity.this);
			mProgressDialog.setTitle(R.string.importingImages);
			mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			mProgressDialog.setOnCancelListener(this);
			mProgressDialog.show();
		}

		@Override
		protected Boolean doInBackground(Void... params)
		{
			final List<Integer> imageHandles = getImageFiles();
			if (imageHandles == null)
			{
                Toast.makeText(CameraImportActivity.this, "USB Error 04: Failed to access storage.", Toast.LENGTH_SHORT).show();
                return false;
			}

			mProgressDialog.setMax(imageHandles.size());

			for (int objectHandle : imageHandles)
			{
				if (cancelled)
				{
					clearWriteResume();
					return false;
				}

				MtpObjectInfo info = mMtpDevice.getObjectInfo(objectHandle);
				if (info == null)
					continue;
				String name = info.getName();
				UsefulDocumentFile endFile = destination.createFile(null, name);
				if (endFile.exists())
					continue;       // Don't count existing files which will be skipped.

				try
				{
					File tmp = new File(getExternalCacheDir(), name);
					mMtpDevice.importFile(objectHandle, tmp.getPath());
					copyFile(Uri.fromFile(tmp), endFile.getUri());
					ContentValues cv = getImageFileInfo(endFile);
					dbInserts.add(cv);
				}
				catch (IOException e)
				{
					e.printStackTrace();
					return false;
				}

				publishProgress();
			}
			return true;
		}

		@Override
		protected void onPostExecute(Boolean success)
		{
			if(mProgressDialog != null && mProgressDialog.isShowing())
				mProgressDialog.dismiss();

			Intent rawdroid = new Intent(CameraImportActivity.this, GalleryActivity.class);

			new AlertDialog.Builder(CameraImportActivity.this)
					.setMessage("Add converted images to the library?")
					.setPositiveButton(R.string.positive, new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialog, int which)
						{
							getContentResolver().bulkInsert(Meta.CONTENT_URI, dbInserts.toArray(new ContentValues[dbInserts.size()]));

							Set<String> uriStrings = new HashSet<>();
							for (ContentValues image : dbInserts)
							{
								uriStrings.add(image.getAsString(Meta.URI));
							}

							Intent broadcast = new Intent(SearchService.Companion.getBROADCAST_SEARCH_COMPLETE())
									.putExtra(SearchService.Companion.getEXTRA_IMAGE_IDS(), uriStrings.toArray(new String[uriStrings.size()]));
							LocalBroadcastManager.getInstance(CameraImportActivity.this).sendBroadcast(broadcast);
						}
					})
					.setNegativeButton(R.string.negative, new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialog, int which)
						{ /*dismiss*/ }
					}).show();

			startActivity(rawdroid);
		}

		@Override
		protected void onProgressUpdate(Void... values)
		{
			mProgressDialog.incrementProgressBy(1);
		}

		@Override
		public void onCancel(DialogInterface dialog)
		{
			cancelled = true;
		}

		public ContentValues getImageFileInfo(@NonNull UsefulDocumentFile file)
		{
			UsefulDocumentFile.FileData fd = file.getCachedData();
			ContentValues cv = new ContentValues();

			if (fd != null)
			{
				cv.put(Meta.NAME, fd.name);
				cv.put(Meta.PARENT, fd.parent.toString());
				cv.put(Meta.TIMESTAMP, fd.lastModified);
			}
			else
			{
				UsefulDocumentFile parent = file.getParentFile();
				if (parent != null)
					cv.put(Meta.PARENT, parent.toString());
			}

			cv.put(Meta.DOCUMENT_ID, file.getDocumentId());
			cv.put(Meta.URI, file.getUri().toString());
			cv.put(Meta.TYPE, ImageUtil.getImageType(CameraImportActivity.this, file.getUri()).getValue());
			return cv;
		}
	}

	private void requestImportImageLocation()
	{
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		startActivityForResult(intent, REQUEST_MTP_IMPORT_DIR);
	}

	@Override
	public void onActivityResult(final int requestCode, int resultCode, final Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode)
		{
			case REQUEST_MTP_IMPORT_DIR:
				if (resultCode == RESULT_OK && data != null)
				{
					handleImportDirResult(data.getData());
				}
				break;
		}
	}

	@Override
	protected void onResumeWriteAction(Enum callingMethod, Object[] callingParameters)
	{
//		switch((WriteActions)callingMethod)
//		{
//			case IMPORT:
//				new ImportTask().execute((List<Integer>)callingParameters[0]);
//				break;
//		}
//		clearWriteResume();
	}

	private void handleImportDirResult(final Uri destinationUri)
	{
		destination = UsefulDocumentFile.fromUri(this, destinationUri);

		getMtpDevice();

		ImportTask task = new ImportTask();
		task.execute();
	}
}
