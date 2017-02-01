package com.anthonymandra.widget;

import android.content.Context;
import android.util.AttributeSet;

public class SquareCheckableRelativeLayout extends CheckableRelativeLayout {

	public SquareCheckableRelativeLayout(Context context) {
		super(context);
	}

	public SquareCheckableRelativeLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public SquareCheckableRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

//	public SquareCheckableRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
//		super(context, attrs, defStyleAttr, defStyleRes);
//	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		// Set a square layout.
		//noinspection SuspiciousNameCombination
		super.onMeasure(widthMeasureSpec, widthMeasureSpec);
	}

}
