package com.anthonymandra.framework;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.annotation.TargetApi;
import android.mtp.MtpDevice;
import android.mtp.MtpObjectInfo;

import com.anthonymandra.dcraw.LibRaw;

@TargetApi(12)
public class MtpImage extends MetaMedia
{
	@SuppressWarnings("unused")
	private static final String TAG = MtpImage.class.getSimpleName();
	private MtpDevice mDevice;
	private int mObjectHandle;
	private MtpObjectInfo mInfo;

	// We would also need to hold the xmp object handle

	public MtpImage(MtpDevice device, int objectHandle)
	{
		mDevice = device;
		mObjectHandle = objectHandle;
		mInfo = mDevice.getObjectInfo(objectHandle);
	}

	@Override
	public byte[] getImage()
	{
		return mDevice.getObject(mObjectHandle, mInfo.getCompressedSize());
	}

	@Override
	public boolean delete()
	{
		return mDevice.deleteObject(mObjectHandle);
	}

	@Override
	public String getName()
	{
		return mInfo.getName();
	}

	@Override
	public String getPath()
	{
		// Ghetto path for mtp so caching can work
		return mDevice.getDeviceName() + File.separator + mInfo.getStorageId() + File.separator + mObjectHandle + File.separator + mInfo.getName();
	}

	public int getHandle()
	{
		return mObjectHandle;
	}

	public String getDeviceName()
	{
		return mDevice.getDeviceName();
	}

	@Override
	public boolean canDecode()
	{
		return LibRaw.canDecode(getImage());
	}

	@Override
	public byte[] getThumb()
	{
		return LibRaw.getThumb(getImage());
	}

	@Override
	public BufferedInputStream getThumbInputStream()
	{
		byte[] imageData = getThumb();
		if (imageData == null)
			return null;

		return new BufferedInputStream(new ByteArrayInputStream(imageData));
	}

	@Override
	public BufferedInputStream getImageInputStream()
	{
		byte[] imageData = getImage();
		if (imageData == null)
			return null;

		return new BufferedInputStream(new ByteArrayInputStream(imageData));
	}

	@Override
	public long getFileSize()
	{
		return mInfo.getCompressedSize();
	}

	@Override
	public boolean rename(String baseName)
	{
		return false;
	}

	@Override
	public void clearXmp()
	{
		// TODO Auto-generated method stub

	}

	@Override
	public boolean readMetadata()
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	protected BufferedInputStream getXmpInputStream()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean hasXmp()
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void writeXmp()
	{
		// TODO Auto-generated method stub
	}

	@Override
	public boolean isDirectory()
	{
		return false;
	}

	@Override
	public boolean copy(File destination)
	{
		File imageDest = new File(destination, getName());
		BufferedInputStream imageStream = getImageInputStream();
		if (imageStream == null)
			return false;
		
		Utils.copy(imageStream, imageDest);
		try
		{
			imageStream.close();
		}
		catch (IOException e)
		{
			return false;
		}

		return true;
	}

	@Override
	public boolean copyThumb(File destination)
	{
		BufferedOutputStream thumbStream = null;
		try
		{
			File thumbDest = new File(destination, Utils.swapExtention(getName(), ".jpg"));
			thumbStream = new BufferedOutputStream(new FileOutputStream(thumbDest));
			thumbStream.write(getThumb()); // Assumes thumb is already in an image format (jpg at time of coding)
		}
		catch (IOException e)
		{
			return false;
		}
		finally
		{
			try
			{
				if (thumbStream != null)
				{
					thumbStream.close();
				}
			}
			catch (IOException e)
			{
				return false;
			}
		}
		return true;
	}

	@Override
	protected BufferedOutputStream getXmpOutputStream()
	{
		return null;
	}
}
