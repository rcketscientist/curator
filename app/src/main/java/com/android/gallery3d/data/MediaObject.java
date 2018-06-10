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

package com.android.gallery3d.data;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.UnsupportedSchemeException;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.android.gallery3d.common.Utils;
import com.anthonymandra.content.Meta;
import com.anthonymandra.framework.FileUtil;
import com.anthonymandra.framework.RawObject;
import com.anthonymandra.framework.UsefulDocumentFile;
import com.crashlytics.android.Crashlytics;

import java.io.File;
import java.io.FileNotFoundException;

public abstract class MediaObject implements RawObject {
    @SuppressWarnings("unused")
    private static final String TAG = "MediaObject";

    protected String mName;
    protected String mType;
    protected Uri mUri;
    protected Context mContext;

    public MediaObject(Context c, Uri uri)
    {
        mUri = uri;
        mContext = c;
    }

    public MediaObject(Context c, Cursor cursor)
    {
        this(c, Uri.parse(cursor.getString(cursor.getColumnIndex(Meta.URI))));

        mName = cursor.getString(cursor.getColumnIndex(Meta.NAME));
        mType = cursor.getString(cursor.getColumnIndex(Meta.TYPE));
    }

    @Override
    public String getName()
    {
        return mName;
    }

    @Override
    public Uri getUri()
    {
        return mUri;
    }

    public void rotate(int degrees) {
        throw new UnsupportedOperationException();
    }

    public Uri getContentUri() {
        String className = getClass().getName();
        Log.e(TAG, "Class " + className + "should implement getContentUri.");
        throw new UnsupportedOperationException();
    }
}
