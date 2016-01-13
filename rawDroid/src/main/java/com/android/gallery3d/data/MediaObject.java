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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import com.anthonymandra.framework.RawObject;

import java.io.File;

public abstract class MediaObject implements RawObject {
    @SuppressWarnings("unused")
    private static final String TAG = "MediaObject";

    protected final String mName;
    protected final long mSize;
    protected final String mType;
    protected final Uri mUri;
    protected Context mContext;

    public MediaObject(Context c, Uri uri)
    {
        mUri = uri;
        mContext = c;
        mType = c.getContentResolver().getType(uri);

        if ("file".equalsIgnoreCase(uri.getScheme()))
        {
            File f = new File(uri.getPath());
            mName = f.getName();
            mSize = f.length();
        }
        else
        {
            Cursor cursor = c.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst())
            {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                mName = cursor.getString(nameIndex);
                mSize = cursor.getLong(sizeIndex);
            }
            else
            {
                mName = null;
                mSize = 0;
            }
        }
    }

    @Override
    public String getName()
    {
        return mName;
    }

    @Override
    public long getFileSize()
    {
        return mSize;
    }

    @Override
    public Uri getUri()
    {
        return mUri;
    }

    @Override
    public String getMimeType()
    {
        return mType;
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
