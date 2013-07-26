package com.anthonymandra.framework;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;

import android.annotation.SuppressLint;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.anthonymandra.dcraw.LibRaw;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.xmp.XmpDirectory;

public class LocalImage extends MetaMedia
{
	private static final String TAG = LocalImage.class.getSimpleName();
	File mImage;
	File mXmp;

	public LocalImage(File image)
	{
		mImage = image;
	}

	// TEMPORARY to avoid full rewrite during testing
	public File getFile()
	{
		return mImage;
	}

	@Override
	public byte[] getImage()
	{
		BufferedInputStream bis = null;
		try
		{
			bis = getImageInputStream();
			if (bis == null)
				return null;
			byte[] imageData = new byte[bis.available()];
			bis.read(imageData);
			return imageData;
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return null;
		}
		finally
		{
			if (bis != null)
			{
				try
				{
					bis.close();
				}
				catch (IOException e)
				{
					// TODO Auto-generated catch block
				}
			}
		}
	}

	@Override
	public boolean delete()
	{
		if (hasXmp())
			getXmpFile().delete();
		return mImage.delete();
	}

	@Override
	public String getName()
	{
		return mImage.getName();
	}

	@Override
	public String getPath()
	{
		return mImage.getPath();
	}

	@Override
	public boolean canDecode()
	{
		if (!mImage.isFile())
			return false;
		return LibRaw.canDecode(mImage);
	}

	@SuppressLint("SimpleDateFormat")
	@Override
	public byte[] getThumb()
	{
		if (Utils.isNativeImage(this))
		{
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(mImage.getPath(), o);
			thumbWidth = o.outWidth;
			thumbHeight = o.outHeight;
			width = o.outWidth;
			height = o.outHeight;
			
			return getImage();
		}

		// byte[] image = getImage();
		// if (image == null)
		// return image;

		String[] exif = new String[12];
		byte[] image = LibRaw.getThumb(mImage, exif);
//		ExifIFD0Directory exifDir = mMetadata.getOrCreateDirectory(ExifIFD0Directory.class);
//		ExifSubIFDDirectory exifSubDir = mMetadata.getOrCreateDirectory(ExifSubIFDDirectory.class);
		try
		{
			setThumbHeight(Integer.parseInt(exif[8]));
			setThumbWidth(Integer.parseInt(exif[9]));
			setHeight(Integer.parseInt(exif[10]));
			setWidth(Integer.parseInt(exif[11]));
		}
		catch (Exception e)
		{
			Log.d(TAG, "Dimensions exif parse failed:", e);
		}
		
		makeLegacy = exif[0];
		modelLegacy = exif[1];
		apertureLegacy = exif[2];
		focalLegacy = exif[3];
		isoLegacy = exif[4];
		shutterLegacy = exif[5];
		
		try
		{
			SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM d hh:mm:ss yyyy");
			dateLegacy = sdf.parse(exif[6].trim());
		}
		catch (Exception e)
		{
			Log.d(TAG, "Date exif parse failed:", e);
		}
		
		try
		{
			orientLegacy = Integer.parseInt(exif[7]);
		}
		catch (Exception e)
		{
			Log.d(TAG, "Orientation exif parse failed:", e);
		}
		
		return image;
	}

	@Override
	public BufferedInputStream getThumbInputStream()
	{
		byte[] thumb = getThumb();
		if (thumb == null)
			return null;
		return new BufferedInputStream(new ByteArrayInputStream(thumb));
	}

	@Override
	public BufferedOutputStream getXmpOutputStream()
	{
		try
		{
			return new BufferedOutputStream(new FileOutputStream(getXmpFile()));
		}
		catch (FileNotFoundException e)
		{
			return null;
		}
	}

	@Override
	public BufferedInputStream getImageInputStream()
	{
		try
		{
			return new BufferedInputStream(new FileInputStream(mImage));
		}
		catch (FileNotFoundException e)
		{
			return null;
		}
	}

	@Override
	public long getFileSize()
	{
		return mImage.length();
	}

	@Override
	public boolean rename(String baseName)
	{
		Boolean imageSuccess = true;
		Boolean xmpSuccess = true;

		String filename = getName();
		String ext = filename.substring(filename.lastIndexOf("."), filename.length());

		String rename = baseName + ext;
		File renameFile = new File(mImage.getParent() + File.separator + rename);
		mImage.renameTo(renameFile);

		if (hasXmpFile())
		{
			rename = baseName + ".xmp";
			File renameXmp = new File(mImage.getParent(), rename);
			xmpSuccess = getXmpFile().renameTo(renameXmp);
		}
		return imageSuccess && xmpSuccess;
	}

	public boolean hasXmp()
	{
		return hasXmpFile() || mMetadata.containsDirectory(XmpDirectory.class);
	}

	private boolean hasXmpFile()
	{
		return getXmpFile().exists();
	}

	private File getXmpFile()
	{
		if (mXmp != null)
			return mXmp;

		String name = mImage.getName();
		int pos = name.lastIndexOf(".");
		if (pos > 0)
		{
			name = name.substring(0, pos);
		}
		name += ".xmp";

		mXmp = new File(mImage.getParent(), name);
		return mXmp;
	}

	public void writeXmp()
	{
		OutputStream os = getXmpOutputStream();
		writeXmp(os);
		try
		{
			if (os != null)
			{
				os.close();
			}
		}
		catch (IOException e1)
		{
			e1.printStackTrace();
		}

	}

	@Override
	protected BufferedInputStream getXmpInputStream()
	{
		if (!hasXmpFile())
			return null;

		try
		{
			return new BufferedInputStream(new FileInputStream(getXmpFile()));
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public boolean isDirectory()
	{
		return mImage.isDirectory();
	}

	@Override
	public boolean copy(File destination)
	{
		if (mXmp != null)
		{
			File xmpDest = new File(destination, mXmp.getName());
			BufferedInputStream xmpStream = getXmpInputStream();
			if (xmpStream != null)
			{
				Utils.copy(xmpStream, xmpDest);
				try
				{
					xmpStream.close();
				}
				catch (IOException e)
				{
				}
			}
		}

		if (mImage == null)
		{
			return false;
		}

		File imageDest = new File(destination, mImage.getName());
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
			byte[] thumb = getThumb();
			if (thumb == null)
				return false;	
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
}
