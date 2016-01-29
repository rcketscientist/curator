package com.anthonymandra.framework;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.support.v4.provider.DocumentFile;
import android.text.TextUtils;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class UsefulDocumentFile
{
    private DocumentFile mDocument;
    private Context mContext;

    // These constants must be kept in sync with DocumentsContract
    private static final String PATH_DOCUMENT = "document";
    private static final String PATH_TREE = "tree";

    private static final String URL_SLASH = "%2F";
    private static final String URL_COLON = "%3A";

    UsefulDocumentFile(Context c, DocumentFile document)
    {
        mDocument = document;
        mContext = c;
    }

    public static UsefulDocumentFile fromFile(Context c, File file)
    {
        return new UsefulDocumentFile(c, DocumentFile.fromFile(file));
    }

    public static UsefulDocumentFile fromUri(Context c, Uri uri)
    {
        if (FileUtil.isFileScheme(uri))
            return new UsefulDocumentFile(c, DocumentFile.fromFile(new File(uri.getPath())));
        else if (DocumentsContract.isDocumentUri(c, uri))
            return new UsefulDocumentFile(c, DocumentFile.fromSingleUri(c, uri));
        else if (isTreeUri(uri))
            return new UsefulDocumentFile(c, DocumentFile.fromTreeUri(c, uri));
        else
            throw new IllegalArgumentException("Invalid URI: " + uri);
    }

    public DocumentFile getDocumentFile()
    {
        return mDocument;
    }

    public UsefulDocumentFile getParentFile()
    {
        DocumentFile parent = mDocument.getParentFile();
        if (parent != null) return new UsefulDocumentFile(mContext, parent);

        return getParentDocument();
    }

    protected static String getRoot(String documentId)
    {
        String[] parts = documentId.split(URL_COLON); // URL :
        if (parts.length > 0)
            return parts[0];
        return null;
    }

    protected static String getPath(String documentId)
    {
        // usb:folder/file.ext would effectively be:
        // content://com.android.externalstorage.documents/tree/0000-0000%3A/document/0000-0000%3Afolder%2Ffile.ext
        // We want to return "folder/file.ext"
        String[] parts = documentId.split(URL_COLON); // URL :

        if (parts.length > 0)
        {
            // The last part should be the path for both document and tree uris
            return parts[parts.length-1];
        }
        return null;
    }

    protected static String[] getPathSegments(String documentId)
    {
        // usb:folder/file.ext would effectively be:
        // content://com.android.externalstorage.documents/tree/0000-0000%3A/document/0000-0000%3Afolder%2Ffile.ext
        // We want to return ["folder", "file.ext"]
        String path = getPath(documentId);
        if (path == null)
            return null;
        return path.split(URL_SLASH); // URL /
    }

	/**
     *  Uri-based DocumentFile do not support parent at all
     *  Try to garner the logical parent through the uri itself
     * @return
     */
    protected UsefulDocumentFile getParentDocument()
    {
        Uri uri = mDocument.getUri();
        String documentId = DocumentsContract.getDocumentId(uri);

        // usb:folder/file.ext would effectively be:
        // content://com.android.externalstorage.documents/tree/0000-0000%3A/document/0000-0000%3Afolder%2Ffile.ext
        // We expect ["folder", "file.ext"] and we want to eventually return:
        // content://com.android.externalstorage.documents/tree/0000-0000%3A/document/0000-0000%3Afolder
        String[] parts = getPathSegments(documentId);

        if (parts == null)
            return null;

        Uri parentUri;

        //  One part means treeUri?
        if (parts.length == 1)
        {
            String parentId = DocumentsContract.getTreeDocumentId(uri);
            parentUri = DocumentsContract.buildTreeDocumentUri(uri.getAuthority(), parentId);
        }
        // document uri
        // take the path segment up to the last segment which will drop file.ext
        // Join them with / and we should have the parent:
        // content://com.android.externalstorage.documents/tree/0000-0000%3A/document/0000-0000%3Afolder
        else
        {
            String[] parentParts = Arrays.copyOfRange(parts, 0, parts.length - 2);
            String parentId = TextUtils.join(URL_SLASH, parentParts);
            parentUri = DocumentsContract.buildDocumentUri(uri.getAuthority(), parentId);
        }

        return UsefulDocumentFile.fromUri(mContext, parentUri);
    }

    public static boolean isTreeUri(Uri uri) {
        final List<String> paths = uri.getPathSegments();
        return (paths.size() == 2 && PATH_TREE.equals(paths.get(0)));
    }

    public boolean canRead()
    {
        return mDocument.canRead();
    }

    public boolean canWrite()
    {
        return mDocument.canWrite();
    }

    public DocumentFile createDirectory(String displayName)
    {
        return mDocument.createDirectory(displayName);
    }

    public DocumentFile createFile(String mimeType, String displayName)
    {
        return mDocument.createFile(mimeType, displayName);
    }

    public boolean delete()
    {
        return mDocument.delete();
    }

    public boolean exists()
    {
        return mDocument.exists();
    }

    public DocumentFile findFile(String displayName)
    {
        return mDocument.findFile(displayName);
    }

    public String getName()
    {
        return mDocument.getName();
    }

    public String getType()
    {
        return mDocument.getType();
    }

    public Uri getUri()
    {
        return mDocument.getUri();
    }

    public boolean isDirectory()
    {
        return mDocument.isDirectory();
    }

    public boolean isFile()
    {
        return mDocument.isFile();
    }

    public long lastModified()
    {
        return mDocument.lastModified();
    }

    public long length()
    {
        return mDocument.length();
    }

    public DocumentFile[] listFiles()
    {
        return mDocument.listFiles();
    }

    public boolean renameTo(String displayName)
    {
        return mDocument.renameTo(displayName);
    }

    public boolean equals(Object o)
    {
        return mDocument.equals(o);
    }

    public int hashCode()
    {
        return mDocument.hashCode();
    }
}
