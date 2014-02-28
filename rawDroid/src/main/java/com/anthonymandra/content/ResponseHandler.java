package com.anthonymandra.content;

import android.content.ContentValues;
import android.net.Uri;

import java.io.IOException;

/**
 * Enables custom handling of HttpResponse and the entities they contain.
 */
public interface ResponseHandler
{
	void handleResponse(ContentValues values, Uri uri) throws IOException;
}
