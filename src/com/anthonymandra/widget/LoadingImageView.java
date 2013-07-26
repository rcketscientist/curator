package com.anthonymandra.widget;

import java.lang.ref.WeakReference;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.ProgressBar;
import android.widget.ViewSwitcher;

import com.anthonymandra.framework.ImageWorker.BitmapWorkerTask;

public class LoadingImageView extends ViewSwitcher
{
	private ProgressBar loadingSpinner;
	private WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

	private ImageView imageView;

	private static final int PROGRESS_VIEW = 0;
	private static final int IMAGE_VIEW = 1;

	private ScaleType scaleType =
	// ScaleType.CENTER;
	ScaleType.CENTER_CROP;

	// ScaleType.CENTER_INSIDE;
	// ScaleType.FIT_CENTER;
	// ScaleType.FIT_END;
	// ScaleType.FIT_START;
	// ScaleType.FIT_XY;
	// ScaleType.MATRIX;

	/**
	 * @param context
	 *            the view's current context
	 */
	public LoadingImageView(Context context)
	{
		super(context);
	}

	public LoadingImageView(Context context, AttributeSet attributes)
	{
		super(context, attributes);
		addLoadingSpinnerView(context);
		addImageView(context);
	}

	private void addLoadingSpinnerView(Context context)
	{
		loadingSpinner = new ProgressBar(context);
		loadingSpinner.setIndeterminate(true);

		LayoutParams lp = new LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.gravity = Gravity.CENTER;
		addView(loadingSpinner, PROGRESS_VIEW, lp);
	}

	private void addImageView(Context context)
	{
		imageView = new ImageView(context);
		imageView.setScaleType(scaleType);
		LayoutParams lp = new LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.gravity = Gravity.CENTER;
		addView(imageView, IMAGE_VIEW, lp);
	}

	public void setImageResource(int resId)
	{
		imageView.setImageResource(resId);
		bitmapWorkerTaskReference = null;
		setDisplayedChild(IMAGE_VIEW);
	}

	public void setImageBitmap(Bitmap b)
	{
		imageView.setImageBitmap(b);
		bitmapWorkerTaskReference = null;
		setDisplayedChild(IMAGE_VIEW);
	}

	public Drawable getDrawable()
	{
		return imageView.getDrawable();
	}

	public void setScaleType(ImageView.ScaleType scale)
	{
		imageView.setScaleType(scale);
	}

	public void setLoadingSpinner()
	{
		bitmapWorkerTaskReference = null;
		setDisplayedChild(PROGRESS_VIEW);
	}

	public void setBitmapWorkerTask(BitmapWorkerTask task)
	{
		bitmapWorkerTaskReference = new WeakReference<BitmapWorkerTask>(task);
	}

	public BitmapWorkerTask getBitmapWorkerTask()
	{
		if (bitmapWorkerTaskReference != null)
			return bitmapWorkerTaskReference.get();
		return null;
	}
}
