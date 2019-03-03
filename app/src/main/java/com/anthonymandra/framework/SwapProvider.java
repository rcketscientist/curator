package com.anthonymandra.framework;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;
import android.widget.Toast;

import com.anthonymandra.imageprocessor.ImageProcessor;
import com.anthonymandra.rawdroid.BuildConfig;
import com.anthonymandra.util.FileUtil;
import com.anthonymandra.util.ImageUtil;
import com.crashlytics.android.Crashlytics;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

@SuppressWarnings("ALL")
public class SwapProvider extends ContentProvider {
	private static final String TAG = SwapProvider.class.getSimpleName();

	// The authority is the symbolic name for the provider class
	public static final String AUTHORITY = BuildConfig.PROVIDER_AUTHORITY_SWAP;

	// UriMatcher used to match against incoming requests
	private UriMatcher uriMatcher;

	@Override
	public boolean onCreate() {
		uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

		// Add a URI to the matcher which will match against the form
		// 'content://com.anthonymandra.rawdroid.swapprovider/*'
		// and return 1 in the case that the incoming Uri matches this pattern
		uriMatcher.addURI(AUTHORITY, "*", 1);
		return true;
	}

	/**
	 * Generates a uri to request a swap file
	 *
	 * @param uri uri of the source image
	 * @return String uri to request a swap file
	 */
	@Nullable
	public static Uri createSwapUri(Context c, Uri uri) {
		if (uri == null) {
			Crashlytics.logException(new Exception("null uri requested swap)"));
			return null;
		}
		return new Uri.Builder()
			.scheme(ContentResolver.SCHEME_CONTENT)
			.authority(SwapProvider.AUTHORITY)
			.path(FileUtil.swapExtention(UsefulDocumentFile.fromUri(c, uri).getName(), "jpg"))
			.fragment(uri.toString())
			.build();
	}

	@Override
	public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode)
		throws FileNotFoundException {
		Uri sourceUri = Uri.parse(uri.getFragment());
		// If it's a native file, just share it directly.
		if (ImageUtil.isNative(sourceUri)) {
			return getContext().getContentResolver().openFileDescriptor(sourceUri, mode);
		}

		String jpg = uri.getPath();

		File swapFile = new File(FileUtil.getDiskCacheDir(getContext(),
			CoreActivity.SWAP_BIN_DIR),
			jpg);

		Log.d(TAG, "Swap.exists(" + swapFile.exists() + "): " + swapFile.getPath());

		// Don't keep recreating the swap file
		// Some receivers may call multiple times
		if (!swapFile.exists()) {
			try {
				swapFile.createNewFile();

				boolean success = false;
				try (
					ParcelFileDescriptor src = getContext().getContentResolver().openFileDescriptor(sourceUri, "r");
					ParcelFileDescriptor dest = ParcelFileDescriptor.open(swapFile, ParcelFileDescriptor.MODE_READ_WRITE)) {
					success = ImageProcessor.writeThumb(src.getFd(), 100, dest.getFd());
				} catch (IOException e) {
					e.printStackTrace();
				}

				if (!success) {
					Handler handler = new Handler(Looper.getMainLooper());
					handler.post(() -> Toast.makeText(getContext(), "Thumbnail generation failed.", Toast.LENGTH_LONG).show());
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return ParcelFileDescriptor.open(swapFile, modeToMode(mode));
	}

	/**
	 * Copied from ContentResolver.java
	 */
	private static int modeToMode(String mode) {
		int modeBits;
		if ("r".equals(mode)) {
			modeBits = ParcelFileDescriptor.MODE_READ_ONLY;
		} else if ("w".equals(mode) || "wt".equals(mode)) {
			modeBits = ParcelFileDescriptor.MODE_WRITE_ONLY
				| ParcelFileDescriptor.MODE_CREATE
				| ParcelFileDescriptor.MODE_TRUNCATE;
		} else if ("wa".equals(mode)) {
			modeBits = ParcelFileDescriptor.MODE_WRITE_ONLY
				| ParcelFileDescriptor.MODE_CREATE
				| ParcelFileDescriptor.MODE_APPEND;
		} else if ("rw".equals(mode)) {
			modeBits = ParcelFileDescriptor.MODE_READ_WRITE
				| ParcelFileDescriptor.MODE_CREATE;
		} else if ("rwt".equals(mode)) {
			modeBits = ParcelFileDescriptor.MODE_READ_WRITE
				| ParcelFileDescriptor.MODE_CREATE
				| ParcelFileDescriptor.MODE_TRUNCATE;
		} else {
			throw new IllegalArgumentException("Invalid mode: " + mode);
		}
		return modeBits;
	}

	// //////////////////////////////////////////////////////////////
	// Not supported / used / required for this example
	// //////////////////////////////////////////////////////////////

	@Override
	public int update(Uri uri, ContentValues contentvalues, String s,
							String[] as) {
		return 0;
	}

	@Override
	public int delete(Uri uri, String s, String[] as) {
		return 0;
	}

	@Override
	public Uri insert(Uri uri, ContentValues contentvalues) {
		return null;
	}

	@Override
	public String getType(Uri uri) {
		return "image/jpeg";
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String s, String[] as1,
							  String s1) {
		return null;
	}
}
