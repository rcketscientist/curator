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
import android.net.Uri;
import android.os.RemoteException;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.android.gallery3d.common.Utils;
import com.anthonymandra.framework.RawObject;
import com.crashlytics.android.Crashlytics;

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

        // TODO: Might want to hang onto the contentresolver
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme()))
        {
            String name = null;
            String type = null;
            long size = 0;

            ContentProviderClient cpc = c.getContentResolver().acquireContentProviderClient(uri);
            Cursor cursor = null;
            try
            {
                if (cpc != null)
                {
                    type = cpc.getType(uri);
                    cursor = cpc.query(uri, null, null, null, null);
                    if (cursor != null && cursor.moveToFirst())
                    {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                        name = cursor.getString(nameIndex);
                        size = cursor.getLong(sizeIndex);
                    }
                }
            }
            catch (RemoteException e)
            {
                Crashlytics.logException(new Exception("Failed to acquire ContentProvider for: " +
                    uri.toString(), e));
            }
            finally
            {
                if (cpc != null)
                    cpc.release();
                Utils.closeSilently(cursor);
                mName = name;
                mType = type;
                mSize = size;
            }
        }
        else    // Should be a file uri
        {
            File f = new File(uri.getPath());
            mName = f.getName();
            mSize = f.length();
            String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            mType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
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
