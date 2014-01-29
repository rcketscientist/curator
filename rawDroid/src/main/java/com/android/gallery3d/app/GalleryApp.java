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

package com.android.gallery3d.app;

import com.android.gallery3d.data.ContentListener;
import com.android.gallery3d.data.ImageCacheService;
import com.android.gallery3d.ui.GLRoot;
import com.anthonymandra.framework.LicenseManager;

public interface GalleryApp extends GalleryContext
{
//    public StateManager getStateManager();
	public GLRoot getGLRoot();

//    public GalleryActionBar getGalleryActionBar();

	public TransitionStore getTransitionStore();

	public ImageCacheService getImageCacheService();

    public void addContentListener(ContentListener listener);
    public void removeContentListener(ContentListener listener);

    public LicenseManager getLicenseManager();
}
