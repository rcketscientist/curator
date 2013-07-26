package com.anthonymandra.framework;

import java.io.BufferedInputStream;
import java.io.File;

public interface MediaObject
{
	public boolean isDirectory();
	
	public boolean delete();

	public String getName();

	public String getPath();

	public boolean canDecode();
	
	public byte[] getImage();

	public byte[] getThumb();

	public BufferedInputStream getThumbInputStream();

	public BufferedInputStream getImageInputStream();

	public long getFileSize();

	public boolean rename(String baseName);
	
	public boolean copy(File destination);
	
	public boolean copyThumb(File destination);
}
