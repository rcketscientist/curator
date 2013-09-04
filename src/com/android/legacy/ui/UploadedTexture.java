/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.legacy.ui;

import com.android.legacy.util.Utils;

import android.graphics.Bitmap;
import android.opengl.GLUtils;

import javax.microedition.khronos.opengles.GL11;
import javax.microedition.khronos.opengles.GL11Ext;

// UploadedTextures use a Bitmap for the content of the texture.
//
// Subclasses should implement onGetBitmap() to provide the Bitmap and
// implement onFreeBitmap(mBitmap) which will be called when the Bitmap
// is not needed anymore.
//
// isContentValid() is meaningful only when the isLoaded() returns true.
// It means whether the content needs to be updated.
//
// The user of this class should call recycle() when the texture is not
// needed anymore.
//
// By default an UploadedTexture is opaque (so it can be drawn faster without
// blending). The user or subclass can override it using setOpaque().
abstract class UploadedTexture extends BasicTexture
{

	@SuppressWarnings("unused")
	private static final String TAG = "Texture";
	private boolean mContentValid = true;
	private boolean mOpaque = true;
	private boolean mThrottled = false;
	private static int sUploadedCount;
	private static final int UPLOAD_LIMIT = 1;

	protected Bitmap mBitmap;

	protected UploadedTexture()
	{
		super(null, 0, STATE_UNLOADED);
	}

	protected void setThrottled(boolean throttled)
	{
		mThrottled = throttled;
	}

	private Bitmap getBitmap() throws DeadBitmapException
	{
		if (mBitmap == null)
		{
			mBitmap = onGetBitmap();
			if (mWidth == UNSPECIFIED)
			{
				setSize(mBitmap.getWidth(), mBitmap.getHeight());
			}
			else if (mWidth != mBitmap.getWidth() || mHeight != mBitmap.getHeight())
			{
				throw new IllegalStateException("cannot change content size");
			}
		}
		return mBitmap;
	}

	private void freeBitmap()
	{
		Utils.Assert(mBitmap != null);
		onFreeBitmap(mBitmap);
		mBitmap = null;
	}

	@Override
	public int getWidth() throws DeadBitmapException
	{
		if (mWidth == UNSPECIFIED)
			getBitmap();
		return mWidth;
	}

	@Override
	public int getHeight() throws DeadBitmapException
	{
		if (mWidth == UNSPECIFIED)
			getBitmap();
		return mHeight;
	}

	protected abstract Bitmap onGetBitmap() throws DeadBitmapException;

	protected abstract void onFreeBitmap(Bitmap bitmap);

	protected void invalidateContent()
	{
		if (mBitmap != null)
			freeBitmap();
		mContentValid = false;
	}

	/**
	 * Whether the content on GPU is valid.
	 */
	public boolean isContentValid(GLCanvas canvas)
	{
		return isLoaded(canvas) && mContentValid;
	}

	/**
	 * Updates the content on GPU's memory.
	 * 
	 * @param canvas
	 * @throws DeadBitmapException
	 */
	public void updateContent(GLCanvas canvas) throws DeadBitmapException
	{
		if (!isLoaded(canvas))
		{
			if (mThrottled && ++sUploadedCount > UPLOAD_LIMIT)
			{
				return;
			}
			uploadToCanvas(canvas);
		}
		else if (!mContentValid)
		{
			Bitmap bitmap = getBitmap();
			int format = GLUtils.getInternalFormat(bitmap);
			int type = GLUtils.getType(bitmap);
			canvas.getGLInstance().glBindTexture(GL11.GL_TEXTURE_2D, mId);
			GLUtils.texSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, bitmap, format, type);
			freeBitmap();
			mContentValid = true;
		}
	}

	public static void resetUploadLimit()
	{
		sUploadedCount = 0;
	}

	public static boolean uploadLimitReached()
	{
		return sUploadedCount > UPLOAD_LIMIT;
	}

	static int[] sTextureId = new int[1];
	static int[] sCropRect = new int[4];

	private void uploadToCanvas(GLCanvas canvas) throws DeadBitmapException
	{
		GL11 gl = canvas.getGLInstance();

		Bitmap bitmap = getBitmap();
		if (bitmap == null)
		{
			mState = STATE_ERROR;
			throw new RuntimeException("Texture load fail, no bitmap");
		}
		else if (bitmap.isRecycled())
		{
			throw new DeadBitmapException();
		}

		try
		{
			// Define a vertically flipped crop rectangle for
			// OES_draw_texture.
			int width = bitmap.getWidth();
			int height = bitmap.getHeight();
			sCropRect[0] = 0;
			sCropRect[1] = height;
			sCropRect[2] = width;
			sCropRect[3] = -height;

			// Upload the bitmap to a new texture.
			gl.glGenTextures(1, sTextureId, 0);
			gl.glBindTexture(GL11.GL_TEXTURE_2D, sTextureId[0]);
			gl.glTexParameteriv(GL11.GL_TEXTURE_2D, GL11Ext.GL_TEXTURE_CROP_RECT_OES, sCropRect, 0);
			gl.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP_TO_EDGE);
			gl.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP_TO_EDGE);
			gl.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			gl.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

			if (width == getTextureWidth() && height == getTextureWidth())
			{
				GLUtils.texImage2D(GL11.GL_TEXTURE_2D, 0, bitmap, 0);
			}
			else
			{
				int format = GLUtils.getInternalFormat(bitmap);
				int type = GLUtils.getType(bitmap);

				gl.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, getTextureWidth(), getTextureHeight(), 0, format, type, null);
				GLUtils.texSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, bitmap, format, type);
			}
		}
		catch (IllegalArgumentException e)
		{
			// This will handle animated gifs
		}
		finally
		{
			freeBitmap();
		}
		// Update texture state.
		setAssociatedCanvas(canvas);
		mId = sTextureId[0];
		mState = UploadedTexture.STATE_LOADED;
	}

	@Override
	protected boolean onBind(GLCanvas canvas) throws DeadBitmapException
	{
		updateContent(canvas);
		return isContentValid(canvas);
	}

	public void setOpaque(boolean isOpaque)
	{
		mOpaque = isOpaque;
	}

	public boolean isOpaque()
	{
		return mOpaque;
	}

	@Override
	public void recycle()
	{
		super.recycle();
		if (mBitmap != null)
			freeBitmap();
	}

	@SuppressWarnings("serial")
	/**
	 * Thrown when a bitmap is null or recycled.  This should be captured and images reset.
	 * @author anthony.mandra
	 *
	 */
	public class DeadBitmapException extends Exception
	{

	}
}
