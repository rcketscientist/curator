package com.anthonymandra.rawdroid

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.ActivityOptions
import android.app.AlertDialog
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.DisplayMetrics
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.afollestad.materialcab.MaterialCab
import com.anthonymandra.framework.*
import com.anthonymandra.rawdroid.data.MetadataEntity
import com.anthonymandra.rawdroid.ui.GalleryAdapter
import com.anthonymandra.rawdroid.ui.GalleryViewModel
import com.anthonymandra.util.ImageUtil
import com.anthonymandra.widget.ItemOffsetDecoration
import com.bumptech.glide.Glide
import com.inscription.WhatsNewDialog
import kotlinx.android.synthetic.main.gallery.*
import java.util.*

open class GalleryActivity : CoreActivity(), GalleryAdapter.OnItemClickListener, GalleryAdapter.OnItemLongClickListener, GalleryAdapter.OnSelectionUpdatedListener {
    override val contentView = R.layout.gallery
    override val selectedImages : Collection<Uri>
        get() { return galleryAdapter.selectedItems }

    private val mResponseIntentFilter = IntentFilter()

    protected lateinit var galleryAdapter: GalleryAdapter

    private var mMaterialCab: MaterialCab? = null
    private var mXmpFilterFragment: XmpFilterFragment? = null

    protected val isContextModeActive: Boolean
        get() = mMaterialCab?.isActive ?: false

    private enum class WriteResume {
        Search
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN)

        setSupportActionBar(galleryToolbar)
        fab.setOnClickListener {
            setWriteResume(WriteResume.Search, emptyArray())
            requestWritePermission()
        }

        filterSidebarButton.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }

        doFirstRun()

        AppRater.app_launched(this)

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

        // Use the displayWidth because the width is without toolbars, don't want to
        // constrain after a rotation.
        val displayWidth = metrics.widthPixels
        val shortSide = Math.min(displayWidth, metrics.heightPixels)

        // we want three divisions on short side, convert that to a column value
        // This will always be 3 in portrait, x in landscape (with 3 rows)
        val thumbSize = (shortSide / 3).toFloat()
        val numColumns = Math.round(displayWidth / thumbSize)
        //TODO: 16:9 => 5 x 2.x or 3 x 5.3, which means rotation will call up slightly different sized thumbs, we need to ensure glide is initially creating the slightly larger variant

        val galleryLayout = GridLayoutManager(this, numColumns)
        galleryLayout.isSmoothScrollbarEnabled = true

        galleryAdapter = GalleryAdapter()
        galleryAdapter.onSelectionChangedListener = this
        galleryAdapter.onItemClickListener = this
        galleryAdapter.onItemLongClickListener = this

        val viewModel = ViewModelProviders.of(this).get(GalleryViewModel::class.java)
        viewModel.imageList.observe(this, Observer {
            galleryAdapter.submitList(it)
            setImageCountTitle(it?.size ?: 0)
        })

        val spacing = ItemOffsetDecoration(this, R.dimen.image_thumbnail_margin)
        galleryView.layoutManager = galleryLayout
        galleryView.addItemDecoration(spacing)
        galleryView.setHasFixedSize(true)
        galleryView.adapter = galleryAdapter

        mXmpFilterFragment = supportFragmentManager.findFragmentById(R.id.filterFragment) as XmpFilterFragment
        mXmpFilterFragment!!.registerXmpFilterChangedListener { filter: XmpFilter ->
            viewModel.updateFilter(filter)
        }
        mXmpFilterFragment!!.registerSearchRootRequestedListener {
            setWriteResume(WriteResume.Search, emptyArray())
            requestWritePermission()
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        mResponseIntentFilter.addAction(MetaService.BROADCAST_IMAGE_PARSED)
        mResponseIntentFilter.addAction(MetaService.BROADCAST_PARSE_COMPLETE)
        mResponseIntentFilter.addAction(SearchService.BROADCAST_SEARCH_STARTED)
        mResponseIntentFilter.addAction(SearchService.BROADCAST_SEARCH_COMPLETE)
        mResponseIntentFilter.addAction(SearchService.BROADCAST_FOUND_IMAGES)
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, mResponseIntentFilter)

        intent.data?.let{ ImageUtil.importKeywords(this, it) }
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
                    contentResolver.openFileDescriptor(it, "r").use {
                        it ?: return
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

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        val settings = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (settings.getBoolean(PREFS_SHOW_FILTER_HINT, true)) {
            drawerLayout.openDrawer(GravityCompat.START)
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
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver)
        super.onDestroy()
    }

    private fun offerRequestPermission() {
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.offerSearchTitle)
            .setMessage(R.string.offerPermissionMessage)
            .setPositiveButton(R.string.search) { _, _ ->
                setWriteResume(WriteResume.Search, emptyArray())
                requestWritePermission()
        }.setNegativeButton(R.string.neutral) { _, _ -> }   //do nothing
        builder.create().show()
    }

    private fun setImageCountTitle(count: Int) {
        supportActionBar?.title = count.toString() + " Images"
    }

    private fun scanRawFiles() {
        toolbarProgress.visibility = View.VISIBLE
        toolbarProgress.isIndeterminate = true        //TODO: Determinate?
        galleryToolbar.subtitle = "Cleaning..."

        MetaDataCleaner.cleanDatabase(this, Handler(Handler.Callback {
            // Upon clean initiate search
            val excludedFolders = mXmpFilterFragment!!.excludedFolders

            val permissionUris = rootPermissions.map { it.uri.toString() }

            SearchService.startActionSearch(
                this@GalleryActivity, null, // Files unsupported on 4.4+
                permissionUris.toTypedArray(),
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
                    Snackbar.make(galleryView, "Tutorial error. Please contact support if this continues.", 5000)
                        .setAction(R.string.contact) { requestEmailIntent("Tutorial Error") }
                        .show()
                }
                // We don't really care about a result, after tutorial offer to search.
                offerRequestPermission()
            }
        }
    }

    private fun handlePhotoUpdate(index: Int) {
        galleryView.smoothScrollToPosition(index)
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
        dataRepo.images(mItemsForIntent.map { it.toString() }).value?.let {
            copyImages(it, destination) // FIXME: GHETTO, threadlock and messy!
        }
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
        val permissibleUsbDevices = HashSet(prefs.getStringSet(PREFS_PERMISSIBLE_USB, emptySet()))

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
                dataRepo.deleteAllImages()
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
            Snackbar.make(galleryView, R.string.warningNoItemsSelected, Snackbar.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, REQUEST_COPY_DIR)
    }

    override fun onImageSetChanged() { }

    override fun onImageAdded(item: Uri) { }

    override fun onImageRemoved(item: Uri) { }

    protected fun removeDatabaseReference(toRemove: Long) {
        dataRepo.deleteImage(toRemove)
    }

    protected fun addDatabaseReference(toAdd: Uri): Long {
        val image = MetadataEntity()
        image.uri = toAdd.toString()

        return dataRepo.insertImages(image).first()
    }

    override fun onSelectionUpdated(selectedUris: Collection<Uri>) {
        mMaterialCab?.setTitle(selectedUris.size.toString() + " " + getString(R.string.selected))
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
            .setMenu(R.menu.gallery_contextual)
            .start(GalleryActionMode())
    }

    protected fun endContextMode() {
        mMaterialCab?.finish()
    }

    override fun onItemLongClick(parent: RecyclerView.Adapter<*>, view: View, position: Int, id: Long) {
        if (!isContextModeActive)
            startContextMode()
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
        if (isContextModeActive) return

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

        // TODO: While this should work, this should pass the db id to be more versatile
        viewer.putExtra(ViewerActivity.EXTRA_START_INDEX, position)

        startActivityForResult(viewer, REQUEST_UPDATE_PHOTO, options)
        view.isDrawingCacheEnabled = false
    }

    private val messageReceiver = object : BroadcastReceiver() {
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
                    val images = intent.getLongArrayExtra(SearchService.EXTRA_IMAGE_IDS)
                    if (images.isEmpty()) {
                        if (mActivityVisible)
                            offerRequestPermission()
                    } else {
                        MetaWakefulReceiver.startMetaService(this@GalleryActivity)
                    }
                }
            }
        }
    }

    companion object {
//        private val TAG = GalleryActivity::class.java.simpleName

        const val LICENSE_RESULT = "license_result"
        const val LICENSE_ALLOW = 1
        const val LICENSE_DISALLOW = 2
        const val LICENSE_ERROR = 3

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
