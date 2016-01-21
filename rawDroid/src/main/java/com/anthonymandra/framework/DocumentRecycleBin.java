package com.anthonymandra.framework;

import android.net.Uri;

import java.io.File;

public class DocumentRecycleBin extends RecycleBin
{
	DocumentActivity mActivity;
	/**
	 * Create new recycling bin with the default parameters.
	 */
	public DocumentRecycleBin(DocumentActivity activity, String uniqueName, int maxSize)
	{
		super(activity, uniqueName, maxSize);
		mActivity = activity;
	}

	/**
	 * @throws DocumentActivity.WritePermissionException thrown if write permission must be requested
	 */
	@Override
	protected boolean deleteFile(File toDelete) throws DocumentActivity.WritePermissionException
	{
		return mActivity.deleteFile(toDelete);
	}

	/**
	 * @throws DocumentActivity.WritePermissionException thrown if write permission must be requested
	 */
	@Override
	protected boolean deleteFile(Uri toDelete) throws DocumentActivity.WritePermissionException
	{
		return mActivity.deleteFile(toDelete);
	}
}
