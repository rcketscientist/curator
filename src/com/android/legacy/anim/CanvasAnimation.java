// Copyright 2010 Google Inc. All Rights Reserved.

package com.android.legacy.anim;

import com.android.legacy.ui.GLCanvas;

public abstract class CanvasAnimation extends Animation
{

	public abstract int getCanvasSaveFlags();

	public abstract void apply(GLCanvas canvas);
}
