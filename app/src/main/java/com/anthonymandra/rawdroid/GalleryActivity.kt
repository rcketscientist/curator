package com.anthonymandra.rawdroid

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.ActivityOptions
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.DocumentsContract
import android.util.DisplayMetrics
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.State
import com.afollestad.materialcab.MaterialCab
import com.anthonymandra.framework.*
import com.anthonymandra.rawdroid.data.MetadataEntity
import com.anthonymandra.rawdroid.ui.GalleryAdapter
import com.anthonymandra.rawdroid.ui.GalleryViewModel
import com.anthonymandra.util.ImageUtil
import com.anthonymandra.widget.ItemOffsetDecoration
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.inscription.WhatsNewDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.gallery.*
import java.util.*

open class GalleryActivity : CoreActivity(), GalleryAdapter.OnItemClickListener, GalleryAdapter.OnItemLongClickListener, GalleryAdapter.OnSelectionUpdatedListener {
	override val contentView = R.layout.gallery
	override val selectedIds: LongArray
		get() {
			return galleryAdapter.selectedItems
		}

	protected lateinit var galleryAdapter: GalleryAdapter

	private var mMaterialCab: MaterialCab? = null
	private var mXmpFilterFragment: XmpFilterFragment? = null
	override val viewModel by lazy { ViewModelProviders.of(this).get(GalleryViewModel::class.java) }
	private var imageCount = 0

	protected val isContextModeActive: Boolean
		get() = mMaterialCab?.isActive ?: false

	public override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		window.setFlags(
				WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN)

		setSupportActionBar(galleryToolbar)
		fab.setOnClickListener {
			requestWritePermission(REQUEST_SEARCH)
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

		viewModel.imageList.observe(this, Observer {
			galleryAdapter.submitList(it)
		})

		// Current image total for title
		viewModel.filteredCount.observe(this, Observer {
			imageCount = it
			galleryToolbar?.title = "$imageCount Images"
		})

		// Current processed image total for subtitle
		viewModel.filteredProcessedCount.observe(this, Observer {
			galleryToolbar.subtitle = if (imageCount == it) null else "$it of $imageCount"
		})

		// Monitor the metadata parse status to display progress
		viewModel.metaReaderStatus.observe(this, Observer {
			if (it == null || it.isEmpty()) {
				return@Observer
			}

			val workStatus = it[0]

			if (workStatus.state.isFinished) {
				galleryToolbar.subtitle = null
				endProgress()
			} else if (workStatus.state == State.RUNNING) {
				toolbarProgress.visibility = View.VISIBLE
				toolbarProgress.isIndeterminate = true
			}
		})

		viewModel.searchStatus.observe(this, Observer {
			if (it == null || it.isEmpty()) {
				return@Observer
			}

			val workStatus = it[0]

			if (workStatus.state.isFinished) {
				galleryToolbar.subtitle = null
				endProgress()
				if (imageCount < 1) {
					//TODO: Alert
				}
			} else if (State.RUNNING == workStatus.state) {
				toolbarProgress.visibility = View.VISIBLE
				toolbarProgress.isIndeterminate = true
				galleryToolbar.subtitle = "Searching..."
			}
		})

		val spacing = ItemOffsetDecoration(this, R.dimen.image_thumbnail_margin)
		galleryView.layoutManager = galleryLayout
		galleryView.addItemDecoration(spacing)
		galleryView.setHasFixedSize(true)
		galleryView.adapter = galleryAdapter

		mXmpFilterFragment = supportFragmentManager.findFragmentById(R.id.filterFragment) as XmpFilterFragment
		mXmpFilterFragment!!.registerXmpFilterChangedListener { filter: ImageFilter ->
			viewModel.setFilter(filter)
		}
		mXmpFilterFragment!!.registerSearchRootRequestedListener {
			requestWritePermission(REQUEST_SEARCH)
			drawerLayout.closeDrawer(GravityCompat.START)
		}

		intent.data?.let { ImageUtil.importKeywords(this, it) }
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

			permissibleUsb?.let { permissions ->
				permissions
						.asSequence()
						.mapNotNull { Uri.parse(it) }
						.forEach {
							contentResolver.openFileDescriptor(it, "r").use { pfd ->
								pfd ?: return
							}
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
			builder.setNegativeButton(R.string.no) { _, _ -> /*do nothing, is there a button w/o this?*/ }
			builder.setPositiveButton(R.string.yes) { _, _ ->
				startActivity(Intent(this@GalleryActivity, TutorialActivity::class.java))
			}

			builder.setMessage(R.string.welcomeTutorial)
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
		whatsNewDialog.show(false)

		galleryAdapter.notifyDataSetChanged()
	}

	public override fun onPause() {
		super.onPause()
	}

	private fun scanRawFiles() {
		toolbarProgress.visibility = View.VISIBLE
		toolbarProgress.isIndeterminate = true
		galleryToolbar.subtitle = "Cleaning..."

		viewModel.startCleanSearchChain()
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
		super.onActivityResult(requestCode, resultCode, intent)

		when (requestCode) {
			REQUEST_COPY_DIR -> if (resultCode == RESULT_OK && intent != null) {
				intent.data?.let { uri ->
					viewModel.startCopyWorker(mItemsForIntent, uri)
				}
			}
			REQUEST_UPDATE_PHOTO -> if (resultCode == RESULT_OK && intent != null) {
				handlePhotoUpdate(intent.getIntExtra(GALLERY_INDEX_EXTRA, 0))
			}
			REQUEST_ACCESS_USB -> if (resultCode == RESULT_OK && intent != null) {
				handleUsbAccessRequest(intent.data)
			}
			REQUEST_TUTORIAL -> {
				if (resultCode == RESULT_ERROR) {
					Snackbar.make(galleryView, "Tutorial error. Please contact support if this continues.", 5000)
							.setAction(R.string.contact) { requestEmailIntent("Tutorial Error") }
							.show()
				}
			}
		}
	}

	private fun handlePhotoUpdate(index: Int) {
		galleryView.smoothScrollToPosition(index)
	}

	override fun setMaxProgress(max: Int) {
		toolbarProgress.max = max
		toolbarProgress.progress = 0
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
				if (rootPermissions.isEmpty()) {
					requestWritePermission(REQUEST_SEARCH)
				} else {
					scanRawFiles()
				}
				return true
			}
			R.id.galleryTutorial -> {
				startActivityForResult(Intent(this@GalleryActivity, TutorialActivity::class.java), REQUEST_TUTORIAL)
				return true
			}
			else -> return super.onOptionsItemSelected(item)
		}
	}

	private fun requestRename() {
		if (galleryAdapter.selectedItemCount == 0)
			selectAll()

		viewModel.images(selectedIds)
				.observeOn(AndroidSchedulers.mainThread())
				.subscribeBy {
					val dialog = RenameDialog(this, it)
					dialog.show()
				}
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

	override fun onSelectionUpdated(selectedIds: LongArray) {
		mMaterialCab?.setTitle(selectedIds.size.toString() + " " + getString(R.string.selected))
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
		viewModel.selectAll()
				.observeOn(AndroidSchedulers.mainThread())
				.subscribeBy { galleryAdapter.selectedItems = it }
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

	@SuppressLint("RestrictedApi")  //startActivityForResult bug
	override fun onItemClick(parent: RecyclerView.Adapter<*>, view: View, position: Int, id: Long) {
		// Don't start an intent while in context mode
		if (isContextModeActive) return

		val viewer = Intent(this, ViewerActivity::class.java)
		viewer.putExtra(ViewerActivity.EXTRA_FILTER, viewModel.filter.value)
		// TODO: While this should work, this should pass the db id to be more versatile
		viewer.putExtra(ViewerActivity.EXTRA_START_INDEX, position)

		val options = Bundle()

		view.isDrawingCacheEnabled = true
		view.isPressed = false
		view.refreshDrawableState()
		options.putAll(
				ActivityOptions.makeThumbnailScaleUpAnimation(
						view, view.drawingCache, 0, 0).toBundle())
		//TODO: If we want this to look smooth we should load the gallery thumb in viewer so there's a smooth transition

		startActivityForResult(viewer, REQUEST_UPDATE_PHOTO, options)
		view.isDrawingCacheEnabled = false
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
		private const val REQUEST_SEARCH = 19

		const val RESULT_ERROR = -111

		const val GALLERY_INDEX_EXTRA = "gallery_index"
	}
}
