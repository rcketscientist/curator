package com.anthonymandra.rawdroid;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.mtp.MtpDevice;
import android.mtp.MtpDeviceInfo;
import android.mtp.MtpObjectInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.anthonymandra.framework.AsyncTask;

import org.openintents.filemanager.FileManagerActivity;
import org.openintents.intents.FileManagerIntents;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
public class CameraImportActivity extends Activity
{
	private static final int REQUEST_MTP_IMPORT_DIR = 3;

	private MtpDevice mMtpDevice;
	private List<Integer> imageHandles = new ArrayList<>();
	private int requiredSpace;
	// private ProgressDialog mProgressDialog;
	private File destination;

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

	private boolean getImageFiles()
	{
		int[] storageIds = mMtpDevice.getStorageIds();
		if (storageIds == null)
		{
			return false;
		}

		requiredSpace = 0;
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
				{
                    String name = info.getName();
                    File endFile = new File(destination, name);
                    if (endFile.exists())
                        continue;       // Don't count existing files which will be skipped.

					requiredSpace += info.getCompressedSize();
					imageHandles.add(objectId);
				}
			}
		}
        return true;
	}

	protected class FindImagesTask extends AsyncTask<Void, Void, Boolean>
	{
		private ProgressDialog mProgressDialog;

		@Override
		protected void onPreExecute()
		{
			mProgressDialog = new ProgressDialog(CameraImportActivity.this);
			MtpDeviceInfo info = mMtpDevice.getDeviceInfo();
            String title = "Import";
            if (info != null)
                 title = info.getManufacturer() + " " + info.getModel();
			mProgressDialog = new ProgressDialog(CameraImportActivity.this);
			mProgressDialog.setTitle(title);
			mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			mProgressDialog.setIndeterminate(true);
			mProgressDialog.show();
		}

		@Override
		protected Boolean doInBackground(Void... params)
		{
			return getImageFiles();
		}

		@Override
		protected void onPostExecute(Boolean result)
		{
            if (!result)
            {
                Toast.makeText(CameraImportActivity.this, "USB Error 04: Failed to access storage.", Toast.LENGTH_SHORT).show();
                return;
            }

			mProgressDialog.dismiss();
			if (destination.getFreeSpace() < requiredSpace)
			{
				Toast.makeText(CameraImportActivity.this, R.string.warningNotEnoughSpace, Toast.LENGTH_LONG).show();
				finish();
			}

			ImportTask it = new ImportTask();
			it.execute();
		}
	}

	protected class ImportTask extends AsyncTask<Void, Void, List<String>> implements OnCancelListener
	{
		private boolean cancelled;
		private ProgressDialog mProgressDialog;

		@Override
		protected void onPreExecute()
		{
			mProgressDialog = new ProgressDialog(CameraImportActivity.this);
			mProgressDialog.setTitle(R.string.importingImages);
			mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			mProgressDialog.setOnCancelListener(this);
			mProgressDialog.setMax(imageHandles.size());
			mProgressDialog.show();
		}

		@Override
		protected List<String> doInBackground(Void... params)
		{
			final List<String> failures = new ArrayList<>();

			for (int objectHandle : imageHandles)
			{
				if (cancelled)
					return failures;
				MtpObjectInfo info = mMtpDevice.getObjectInfo(objectHandle);
				if (info == null)
					continue;
				String name = info.getName();
                File endFile = new File(destination, name);
                if (endFile.exists())
                    continue;   //skip files if re-importing

				boolean success = mMtpDevice.importFile(objectHandle, endFile.getPath());
				if (!success)
					failures.add(name);
				publishProgress();
			}
			return failures;
		}

		@Override
		protected void onPostExecute(List<String> failed)
		{
			if(mProgressDialog != null && mProgressDialog.isShowing())
				mProgressDialog.dismiss();

			if (failed.size() > 0)
			{
				String failures = "Failed files: ";
				for (String fail : failed)
				{
					failures += fail + ", ";
				}
				Toast.makeText(CameraImportActivity.this, failures, Toast.LENGTH_LONG).show();
			}

			Intent rawdroid = new Intent(CameraImportActivity.this, GalleryActivity.class);
			rawdroid.putExtra(GalleryActivity.KEY_STARTUP_DIR, destination.getPath());
			startActivity(rawdroid);
		}

		@Override
		protected void onProgressUpdate(Void... values)
		{
			mProgressDialog.incrementProgressBy(1);
			// setSupportProgress(values[0]);
		}

		@Override
		public void onCancel(DialogInterface dialog)
		{
			cancelled = true;
		}
	}

	private void requestImportImageLocation()
	{
//		Intent images = new Intent(FileManagerIntents.ACTION_PICK_DIRECTORY);
        Intent intent = new Intent(this, FileManagerActivity.class);
        intent.setAction(FileManagerIntents.ACTION_PICK_DIRECTORY);

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		String recentImport = settings.getString(GalleryActivity.PREFS_MOST_RECENT_IMPORT, null);
		File importLocation = GalleryActivity.START_PATH;
		if (recentImport != null)
		{
			importLocation = new File(recentImport);
			if (!importLocation.exists())
			{
				importLocation = GalleryActivity.START_PATH;
			}
		}

		// Construct URI from file name.
        intent.setData(Uri.fromFile(importLocation));

		// Set fancy title and button (optional)
        intent.putExtra(FileManagerIntents.EXTRA_TITLE, getString(R.string.chooseDestination));
        intent.putExtra(FileManagerIntents.EXTRA_BUTTON_TEXT, getString(R.string.import1));

		try
		{
			startActivityForResult(intent, REQUEST_MTP_IMPORT_DIR);
		}
		catch (ActivityNotFoundException e)
		{
			// No compatible file manager was found.
			Toast.makeText(this, R.string.no_filemanager_installed, Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	public synchronized void onActivityResult(final int requestCode, int resultCode, final Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode)
		{
			case REQUEST_MTP_IMPORT_DIR:
				if (resultCode == RESULT_OK && data != null)
				{
					handleImportDirResult(data.getData().getPath());
				}
				break;
		}
	}

	private void handleImportDirResult(final String destinationPath)
	{
		destination = new File(destinationPath);

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString(GalleryActivity.PREFS_MOST_RECENT_IMPORT, destination.getPath());
		editor.apply();

		getMtpDevice();

		FindImagesTask fit = new FindImagesTask();
		fit.execute();
		// getImageFiles();
		//
		// if (destination.getFreeSpace() < requiredSpace)
		// {
		// Toast.makeText(this, R.string.warningNotEnoughSpace, Toast.LENGTH_LONG).show();
		// return;
		// }
		//
		// ImportTask it = new ImportTask(destination);
		// it.execute(imageHandles);
	}
}
