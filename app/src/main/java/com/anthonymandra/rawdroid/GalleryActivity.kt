package com.anthonymandra.rawdroid

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.ActivityOptions
import android.app.AlertDialog
import android.content.*
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.provider.DocumentsContract
import android.support.design.widget.Snackbar
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.DisplayMetrics
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.Toast
import com.afollestad.materialcab.MaterialCab
import com.android.gallery3d.common.Utils
import com.anthonymandra.content.Meta
import com.anthonymandra.framework.*
import com.anthonymandra.rawdroid.ui.GalleryAdapter
import com.anthonymandra.util.DbUtil
import com.anthonymandra.util.ImageUtil
import com.anthonymandra.widget.ItemOffsetDecoration
import com.bumptech.glide.Glide
import com.inscription.WhatsNewDialog
import kotlinx.android.synthetic.main.gallery.*
import java.util.*

open class GalleryActivity : CoreActivity(), GalleryAdapter.OnItemClickListener, GalleryAdapter.OnItemLongClickListener, GalleryAdapter.OnSelectionUpdatedListener {
    override val contentView = R.layout.gallery
    override val progressBar: ProgressBar = toolbarProgress
    override val licenseHandler = CoreActivity.LicenseHandler(this) //FIXME:!!
    override val selectedImages = galleryAdapter.selectedItems


    private val mResponseIntentFilter = IntentFilter()
    
    protected lateinit var galleryAdapter: GalleryAdapter   //TODO: Attach to lifecycle?

    private var mMaterialCab: MaterialCab? = null
    private var mXmpFilterFragment: XmpFilterFragment? = null
    private var mDrawerLayout: DrawerLayout? = null

    protected val isContextModeActive: Boolean
        get() = mMaterialCab !=
            null && mMaterialCab!!.isActive

    private enum class WriteResume {
        Search
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN)

        setSupportActionBar(galleryToolbar)

        filterSidebarButton.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }

        filterFragment
        mXmpFilterFragment = supportFragmentManager.findFragmentById(R.id.filterFragment) as XmpFilterFragment
        mXmpFilterFragment!!.registerXmpFilterChangedListener { filter: XmpFilter ->
            updateMetaLoaderXmp(filter)
            Unit
        }
        mXmpFilterFragment!!.registerSearchRootRequestedListener {
            setWriteResume(WriteResume.Search, null)
            requestWritePermission()
            mDrawerLayout!!.closeDrawer(GravityCompat.START)
            Unit
        }

        doFirstRun()

        AppRater.app_launched(this)

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

        val mDisplayWidth = metrics.widthPixels
        val shortSide = Math.min(mDisplayWidth, metrics.heightPixels)

        // we want three divisions on short side, convert that to a column value
        // This will always be 3 in portrait, x in landscape (with 3 rows)
        val thumbSize = (shortSide / 3).toFloat()
        val numColumns = Math.round(mDisplayWidth / thumbSize)
        //TODO: 16:9 => 5 x 2.x or 3 x 5.3, which means rotation will call up slightly different sized thumbs, we need to ensure glide is initially creating the slightly larger variant

        val mGridLayout = GridLayoutManager(this, numColumns)
        mGridLayout.isSmoothScrollbarEnabled = true

        galleryAdapter = GalleryAdapter()
        galleryAdapter.onSelectionChangedListener = this
        galleryAdapter.onItemClickListener = this
        galleryAdapter.onItemLongClickListener = this

        val spacing = ItemOffsetDecoration(this, R.dimen.image_thumbnail_margin)
        gridview.layoutManager = mGridLayout
        gridview.addItemDecoration(spacing)
        gridview.setHasFixedSize(true)
        gridview.adapter = galleryAdapter

        mResponseIntentFilter.addAction(MetaService.BROADCAST_IMAGE_PARSED)
        mResponseIntentFilter.addAction(MetaService.BROADCAST_PARSE_COMPLETE)
        mResponseIntentFilter.addAction(SearchService.BROADCAST_SEARCH_STARTED)
        mResponseIntentFilter.addAction(SearchService.BROADCAST_SEARCH_COMPLETE)
        mResponseIntentFilter.addAction(SearchService.BROADCAST_FOUND_IMAGES)
        LocalBroadcastManager.getInstance(this).registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    MetaService.BROADCAST_IMAGE_PARSED -> galleryToolbar.subtitle = StringBuilder()
                        .append(intent.getIntExtra(MetaService.EXTRA_COMPLETED_JOBS, -1))
                        .append(" of ")
                        .append(intent.getIntExtra(MetaService.EXTRA_TOTAL_JOBS, -1))//mGalleryAdapter.getCount()));
                    MetaService.BROADCAST_PROCESSING_COMPLETE -> galleryToolbar.subtitle = "Updating..."
                    MetaService.BROADCAST_PARSE_COMPLETE -> {
                        toolbarProgress.visibility = View.GONE
                        galleryToolbar.subtitle = ""
                    }
                    SearchService.BROADCAST_SEARCH_STARTED -> {
                        toolbarProgress.visibility = View.VISIBLE
                        toolbarProgress.isIndeterminate = true
                        galleryToolbar.subtitle = "Searching..."
                    }
                    SearchService.BROADCAST_FOUND_IMAGES -> galleryToolbar.title = intent.getIntExtra(SearchService.EXTRA_NUM_IMAGES, 0).toString() + " Images"
                    SearchService.BROADCAST_SEARCH_COMPLETE -> {
                        val images = intent.getStringArrayExtra(SearchService.EXTRA_IMAGE_URIS)
                        if (images.isEmpty()) {
                            if (mActivityVisible)
                                offerRequestPermission()
                        } else {
                            MetaWakefulReceiver.startMetaService(this@GalleryActivity)
                        }
                    }
                }
            }
        }, mResponseIntentFilter)
        setImageCountTitle()

        if (intent.data != null) {
            ImageUtil.importKeywords(this, intent.data)
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED == intent.action) {
            /* There is absolutely no way to uniquely identify a usb device across connections
			 So what we'll do instead is rely on the funky SAF host as a unique ID
			 The flaw here is the severe edge case that in a multi-device situation we will not
			 request permission for additional devices and jump out at the first recognized device.
			 Well that and the fact Google will break all this in 6.1 */

            val permissibleUsb = PreferenceManager.getDefaultSharedPreferences(this)
                .getStringSet(PREFS_PERMISSIBLE_USB, HashSet())
            permissibleUsb
                .asSequence()
                .map { Uri.parse(it) }
                .forEach {
                    try {
                        val pfd = contentResolver.openFileDescriptor(it, "r")
                        // If pfd exists then this is a reconnected device, avoid hassling user
                        if (pfd != null) {
                            Utils.closeSilently(pfd)
                            return
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

            // Since this appears to be a new device gather uri and request write permission
            val request = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            request.putExtra(DocumentsContract.EXTRA_PROMPT, getString(R.string.selectUsb))
            startActivityForResult(request, REQUEST_ACCESS_USB)
        } else if (getIntent().data != null) {
            ImageUtil.importKeywords(this, getIntent().data)
        }
    }

    //	protected void updateQuery(XmpFilter filter) {
    //		((App)getApplication()).getDatabase().metadataDao().getImages(
    //				filter.andTrueOrFalse,
    //				filter.sortColumn == XmpFilter.SortColumns.Name,
    //				filter.segregateByType,
    //				filter.sortAscending,
    //				Arrays.asList(filter.xmp.label),
    //				Arrays.asList(filter.xmp.subject),
    //				filter.hiddenFolders,
    //				Arrays.asList(filter.xmp.rating)
    //		)
    //	}

    @SuppressLint()
    private fun updateMetaLoaderXmp(filter: XmpFilter) {
        val selection = StringBuilder()
        val selectionArgs = ArrayList<String>()
        var requiresJoiner = false

        val and = " AND "
        val or = " OR "
        val joiner = if (filter.andTrueOrFalse) and else or

        if (filter.xmp != null) {
            if (filter.xmp.label.isNotEmpty()) {
                requiresJoiner = true

                selection.append(DbUtil.createIN(Meta.LABEL, filter.xmp.label.size))
                selectionArgs.addAll(filter.xmp.label)
            }
            //			if (filter.xmp.subject != null && filter.xmp.subject.length > 0)
            //			{
            //				if (requiresJoiner)
            //					selection.append(joiner);
            //				requiresJoiner = true;
            //
            //				selection.append(DbUtil.createLike(Meta.SUBJECT, filter.xmp.subject,
            //						selectionArgs, joiner, false,
            //						"%", "%",   // openended wildcards, match subject anywhere
            //						null));
            //			}
            if (filter.xmp.rating.isNotEmpty()) {
                if (requiresJoiner)
                    selection.append(joiner)
                requiresJoiner = true

                selection.append(DbUtil.createIN(Meta.RATING, filter.xmp.rating.size))
                filter.xmp.rating.mapTo(selectionArgs) { java.lang.Double.toString(it.toDouble()) }
            }
        }
        if (filter.hiddenFolders.isNotEmpty()) {
            if (requiresJoiner)
                selection.append(and)  // Always exclude the folders, don't OR

            selection.append(DbUtil.createLike(Meta.PARENT,
                filter.hiddenFolders.toTypedArray(),
                selectionArgs,
                and, // Requires AND so multiple hides don't negate each other
                true, null, // No wild to start, matches path exactly
                "%", // Wildcard end to match all children
                "%")// NOT
            )  // Uri contain '%' which means match any so escape them
        }

        val order = if (filter.sortAscending) " ASC" else " DESC"
        val sort = StringBuilder()

        if (filter.segregateByType) {
            sort.append(Meta.TYPE).append(" COLLATE NOCASE").append(" ASC, ")
        }
        when (filter.sortColumn) {
            XmpFilter.SortColumns.Date -> sort.append(Meta.TIMESTAMP).append(order)
            XmpFilter.SortColumns.Name -> sort.append(Meta.NAME).append(" COLLATE NOCASE").append(order)
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        // This must be here due to the lifecycle
        updateMetaLoaderXmp(mXmpFilterFragment!!.xmpFilter)

        val settings = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (settings.getBoolean(PREFS_SHOW_FILTER_HINT, true)) {
            mDrawerLayout!!.openDrawer(GravityCompat.START)
            val editor = settings.edit()
            editor.putBoolean(PREFS_SHOW_FILTER_HINT, false)
            editor.apply()
        }
    }

    private fun doFirstRun() {
        val settings = PreferenceManager.getDefaultSharedPreferences(this)
        if (settings.getBoolean("isFirstRun", true)) {
            val editor = settings.edit()
            editor.putBoolean("isFirstRun", false)
            editor.apply()

            val builder = AlertDialog.Builder(this)
            builder.setTitle(R.string.welcomeTitle)
            builder.setNegativeButton(R.string.no) { _, _ -> offerRequestPermission() }
            builder.setPositiveButton(R.string.yes) { _, _ -> startActivity(Intent(this@GalleryActivity, TutorialActivity::class.java)) }

            if (Constants.VariantCode > 9) {
                builder.setMessage(R.string.welcomeTutorial)
            } else {
                builder.setMessage(R.string.welcomeMessage)
            }

            builder.show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.gallery_options, menu)
        return true
    }

    public override fun onResume() {
        super.onResume()

        // Launch what's new dialog (will only be shown once)
        val whatsNewDialog = WhatsNewDialog(this)
        whatsNewDialog.show(Constants.VariantCode == 8)

        galleryAdapter.notifyDataSetChanged()
    }

    public override fun onPause() {
        super.onPause()
    }

    public override fun onDestroy() {
        super.onDestroy()
    }

    private fun offerRequestPermission() {
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.offerSearchTitle)
            .setMessage(R.string.offerPermissionMessage)
            .setPositiveButton(R.string.search) { _, _ ->
                setWriteResume(WriteResume.Search, null)
                requestWritePermission()
        }.setNegativeButton(R.string.neutral) { _, _ -> }   //do nothing
        builder.create().show()
    }

    private fun setImageCountTitle() {
        supportActionBar?.title = galleryAdapter.itemCount.toString() + " Images"
    }

    private fun scanRawFiles() {
        toolbarProgress.visibility = View.VISIBLE
        toolbarProgress.isIndeterminate = true        //TODO: Determinate?
        galleryToolbar.subtitle = "Cleaning..."

        MetaDataCleaner.cleanDatabase(this, Handler(Handler.Callback {
            // Upon clean initiate search
            val excludedFolders = mXmpFilterFragment!!.excludedFolders

            val rootPermissions = rootPermissions
            val size = rootPermissions.size
            val permissions = arrayOfNulls<String>(size)
            for (i in 0 until size) {
                permissions[i] = rootPermissions[i].uri.toString()
            }

            SearchService.startActionSearch(
                this@GalleryActivity, null, // Files unsupported on 4.4+
                permissions,
                excludedFolders!!.toTypedArray())
            true
        }))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_COPY_DIR -> if (resultCode == RESULT_OK && data != null) {
                handleCopyDestinationResult(data.data)
            }
            REQUEST_UPDATE_PHOTO -> if (resultCode == RESULT_OK && data != null) {
                handlePhotoUpdate(data.getIntExtra(GALLERY_INDEX_EXTRA, 0))
            }
            REQUEST_ACCESS_USB -> if (resultCode == RESULT_OK && data != null) {
                handleUsbAccessRequest(data.data)
            }
            REQUEST_TUTORIAL -> {
                if (resultCode == RESULT_ERROR) {
                    Snackbar.make(gridview, "Tutorial error. Please contact support if this continues.", 5000)
                        .setAction(R.string.contact) { requestEmailIntent("Tutorial Error") }
                        .show()
                }
                // We don't really care about a result, after tutorial offer to search.
                offerRequestPermission()
            }
        }
    }

    private fun handlePhotoUpdate(index: Int) {
        gridview.smoothScrollToPosition(index)
    }

    private fun handleCopyDestinationResult(destination: Uri) {
        // TODO: Might want to figure out a way to get free space to introduce this check again
        //		long importSize = getSelectedImageSize();
        //		if (destination.getFreeSpace() < importSize)
        //		{
        //			Toast.makeText(this, R.string.warningNotEnoughSpace, Toast.LENGTH_LONG).show();
        //			return;
        //		}

        //		new CopyTask().execute(mItemsForIntent, destination);
        copyImages(mItemsForIntent, destination)
    }

    override fun updateMessage(message: String?) {
        galleryToolbar.subtitle = message
    }

    override fun setMaxProgress(max: Int) {
        toolbarProgress.max = max
        toolbarProgress.isIndeterminate = false
        toolbarProgress.visibility = View.VISIBLE

    }

    override fun incrementProgress() {
        toolbarProgress.incrementProgressBy(1)
    }

    override fun endProgress() {
        toolbarProgress.visibility = View.GONE
    }

    //	private long getSelectedImageSize()
    //	{
    //		long selectionSize = 0;
    //		for (Uri selected : mGalleryAdapter.getSelectedItems())
    //		{
    //			UsefulDocumentFile df = UsefulDocumentFile.fromUri(this, selected);
    //			selectionSize += df.length();
    //		}
    //		return selectionSize;
    //	}

    private fun handleUsbAccessRequest(treeUri: Uri?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        // You must make a copy of the returned preference set or changes will not be recognized
        val permissibleUsbDevices = HashSet(prefs.getStringSet(PREFS_PERMISSIBLE_USB, HashSet())!!)

        // The following oddity is because permission uris are not valid without SAF
        val makeUriUseful = UsefulDocumentFile.fromUri(this, treeUri!!)
        permissibleUsbDevices.add(makeUriUseful.uri.toString())

        val editor = prefs.edit()
        editor.putStringSet(PREFS_PERMISSIBLE_USB, permissibleUsbDevices)
        editor.apply()

        contentResolver.takePersistableUriPermission(treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    private fun storeSelectionForIntent() {
        mItemsForIntent = galleryAdapter.selectedItems
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.contextRename -> {
                requestRename()
                return true
            }
            R.id.galleryClearCache -> {
                Thread(Runnable { Glide.get(this@GalleryActivity).clearDiskCache() }).start()
                Glide.get(this@GalleryActivity).clearMemory()
                contentResolver.delete(Meta.CONTENT_URI, null, null)
                Toast.makeText(this, R.string.cacheCleared, Toast.LENGTH_SHORT).show()
                return true
            }
            R.id.contextCopy -> {
                requestCopyDestination()
                return true
            }
            R.id.menu_selectAll -> {
                selectAll()
                return false
            }
            R.id.galleryRefresh -> {
                if (rootPermissions.size == 0) {
                    offerRequestPermission()
                } else {
                    scanRawFiles()
                }
                return true
            }
            R.id.galleryTutorial -> {
                startActivityForResult(Intent(this@GalleryActivity, TutorialActivity::class.java), REQUEST_TUTORIAL)
                return true
            }
            R.id.gallerySd -> {
                requestWritePermission()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun requestRename() {
        if (galleryAdapter.selectedItemCount == 0)
            galleryAdapter.selectAll()

        showRenameDialog(selectedImages)
    }

    private fun requestCopyDestination() {
        storeSelectionForIntent()
        if (mItemsForIntent.isEmpty()) {
            Snackbar.make(gridview, R.string.warningNoItemsSelected, Snackbar.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, REQUEST_COPY_DIR)
    }

    override fun onImageSetChanged() {
        // Not needed with a cursorloader
        //TODO: This could be used to batch adds/removes
    }

    override fun onImageAdded(item: Uri) {
        //not needed with cursorloader
        //		addDatabaseReference(item);
    }

    override fun onImageRemoved(item: Uri) {
        //not needed with cursorloader
        //		removeDatabaseReference(item);
    }

    protected fun removeDatabaseReference(toRemove: Uri): Boolean {
        val rowsDeleted = contentResolver.delete(
            Meta.CONTENT_URI,
            Meta.URI_SELECTION,
            arrayOf(toRemove.toString()))
        return rowsDeleted > 0
    }

    protected fun addDatabaseReference(toAdd: Uri): Uri? {
        val cv = ContentValues()
        cv.put(Meta.URI, toAdd.toString())
        //		ImageUtil.getContentValues(this, toAdd, cv);

        return contentResolver.insert(Meta.CONTENT_URI, cv)
    }

    override fun onSelectionUpdated(selectedUris: Collection<Uri>) {
        if (mMaterialCab != null) {
            mMaterialCab!!.setTitle(selectedUris.size.toString() + " " + getString(R.string.selected))
        }
        xmpEditFragment.reset()   // reset the panel to ensure it's clear it's not tied to existing values
    }

    private inner class GalleryActionMode : MaterialCab.Callback {
        override fun onCabCreated(cab: MaterialCab, menu: Menu): Boolean {
            return true
        }

        override fun onCabItemClicked(item: MenuItem): Boolean {
            val handled = onOptionsItemSelected(item)
            if (handled)
                endContextMode()
            return handled
        }

        override fun onCabFinished(cab: MaterialCab): Boolean {
            galleryAdapter.multiSelectMode = false
            mMaterialCab = null
            return true
        }
    }

    fun selectAll() {
        startContextMode()
        galleryAdapter.selectAll()
    }

    protected fun startContextMode() {
        mMaterialCab = MaterialCab(this, R.id.cab_stub)
            .setTitle(getString(R.string.selectItems))
            .setMenu(R.menu.gallery_contextual)
            .start(GalleryActionMode())
    }

    protected fun endContextMode() {
        if (mMaterialCab != null)
            mMaterialCab!!.finish()
    }

    override fun onItemLongClick(parent: RecyclerView.Adapter<*>, view: View, position: Int, id: Long): Boolean {
        startContextMode()
        return true
    }

    override fun onResumeWriteAction(callingMethod: Enum<*>?, callingParameters: Array<Any>) {
        super.onResumeWriteAction(callingMethod, callingParameters)
        if (callingMethod == null)
            return

        when (callingMethod as WriteResume?) {
            GalleryActivity.WriteResume.Search -> scanRawFiles()
        }
    }

    @SuppressLint("RestrictedApi")  //startActivityForResult bug
    override fun onItemClick(parent: RecyclerView.Adapter<*>, view: View, position: Int, id: Long) {
        val uri = galleryAdapter.getUri(position)
        // Don't start an intent while in context mode
        if (galleryAdapter.multiSelectMode) return

        val viewer = Intent(this, ViewerChooser::class.java)
        viewer.data = uri

        val options = Bundle()

        view.isDrawingCacheEnabled = true
        view.isPressed = false
        view.refreshDrawableState()
        options.putAll(
            ActivityOptions.makeThumbnailScaleUpAnimation(
                view, view.drawingCache, 0, 0).toBundle())
        //TODO: If we want this to look smooth we should load the gallery thumb in viewer so there's a smooth transition

        // TODO: We need to control the viewer query somehow
//        viewer.putExtra(CoreActivity.EXTRA_META_BUNDLE, metaLoader)
        viewer.putExtra(ViewerActivity.EXTRA_START_INDEX, position)

        startActivityForResult(viewer, REQUEST_UPDATE_PHOTO, options)
        view.isDrawingCacheEnabled = false
    }

    companion object {
//        private val TAG = GalleryActivity::class.java.simpleName

        // Preference fields
        const val PREFS_NAME = "RawDroidPrefs"
        const val PREFS_SHOW_FILTER_HINT = "prefShowFilterHint"
        const val PREFS_PERMISSIBLE_USB = "prefPermissibleUsb"

        // Request codes
        private const val REQUEST_COPY_DIR = 12
        private const val REQUEST_UPDATE_PHOTO = 16
        private const val REQUEST_ACCESS_USB = 17
        private const val REQUEST_TUTORIAL = 18

        const val RESULT_ERROR = -111

        const val GALLERY_INDEX_EXTRA = "gallery_index"
    }
}
