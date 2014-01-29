package com.anthonymandra.content;

import java.io.IOException;

import android.content.ContentValues;
import android.net.Uri;

/**
 * Enables custom handling of HttpResponse and the entities they contain.
 */
public interface ResponseHandler
{
	void handleResponse(ContentValues values, Uri uri) throws IOException;
}
