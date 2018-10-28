package com.anthonymandra.rawdroid

import android.app.WallpaperManager
import android.content.Intent
import android.content.Intent.*
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.paging.PagedList
import androidx.viewpager.widget.ViewPager
import com.anthonymandra.framework.CoreActivity
import com.anthonymandra.framework.SwapProvider
import com.anthonymandra.rawdroid.data.FolderEntity
import com.anthonymandra.rawdroid.data.ImageInfo
import com.anthonymandra.rawdroid.data.TempViewerDataSource
import com.anthonymandra.rawdroid.ui.GalleryViewModel
import com.anthonymandra.rawdroid.ui.ViewerAdapter
import com.anthonymandra.util.AppExecutors
import com.anthonymandra.util.ImageUtil
import com.anthonymandra.util.MetaUtil
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.viewer_pager.*
import java.util.*

class ViewerActivity : CoreActivity() {
	override val contentView = R.layout.viewer_pager
	private lateinit var viewerAdapter: ViewerAdapter
	private var currentImage: ImageInfo? = null

	private var autoHide = Timer()

	override val selectedIds: LongArray
		get() = listOfNotNull(viewerAdapter.getImage(pager.currentItem)?.id).toLongArray()

	override val viewModel: GalleryViewModel by lazy {
		ViewModelProviders.of(this).get(GalleryViewModel::class.java)
	}

	private var displayWidth = 0
	private var displayHeight = 0

	override fun onCreate(savedInstanceState: Bundle?) {
		theme.applyStyle(FullSettingsActivity.getMetaStyle(this), true)   //must be called before setContentView

		super.onCreate(savedInstanceState)

		setSupportActionBar(viewerToolbar)
		supportActionBar?.setDisplayShowTitleEnabled(false)

		val metrics = DisplayMetrics()
		windowManager.defaultDisplay.getMetrics(metrics)
		displayWidth = metrics.widthPixels
		displayHeight = metrics.heightPixels

		val settings = PreferenceManager.getDefaultSharedPreferences(this)
		isImmersive = settings.getBoolean(FullSettingsActivity.KEY_UseImmersive, true)

		viewerAdapter = ViewerAdapter(supportFragmentManager)

		val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)

		if (intent.action == ACTION_VIEW || intent.action == ACTION_SEND) {	// External, add data
			val data = intent.data
			if (data == null) {
				Toast.makeText(this, "Path could not be found, please email me if this continues", Toast.LENGTH_LONG).show()
            finish()
			} else {
				createTempDataSource(listOf(data))
			}
		} else if (intent.action == ACTION_SEND_MULTIPLE) {	// External, add data
			val stream = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
			createTempDataSource(stream)
		} else {    // Gallery intent
			viewModel.setFilter(intent.getParcelableExtra(EXTRA_FILTER))
			viewModel.imageList.observe(this, Observer {
				viewerAdapter.submitList(it)
				// TODO: Can we get an update?  Will the entries reorder on changes?
				// If se we could likely use the a custom data source like the temp shares
				pager.setCurrentItem(startIndex, false)
			})
		}

		viewModel.navigationVisibility.observe(this, Observer { visible ->
			layoutNavButtons.visibility = visible
		})

		viewModel.toolbarVisibility.observe(this, Observer { visible ->
			if (visible)
				supportActionBar?.show()
			else
				supportActionBar?.hide()
		})

		pager.adapter = viewerAdapter
		pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
			override fun onPageScrollStateChanged(state: Int) {}
			override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
			override fun onPageSelected(position: Int) {
				viewModel.showInterface()
				currentImage = viewerAdapter.getImage(position)
				currentImage?.let {
					xmpEditFragment.initXmp(
							it.rating?.toInt(),
							Collections.emptyList()/*it.subjectIds*/,
							it.label)
				}
				autoHide.cancel()
				autoHide = Timer()
				autoHide.schedule(AutoHideMetaTask(), 3000)
			}
		})
		// TODO: Jetifier not working on page transformer
//        pager.setPageTransformer(true, DepthPageTransformer())
		pager.offscreenPageLimit = 2

		// TODO: We need some way to update meta when processed.  This is probably done automatically
		// with the viewmodel, but that may change sorting.  This was previously done with a broadcast.
		// We could likely use a MetaReadWorker observer to check if the current image changed.

		imageButtonNext.setOnClickListener { pager.currentItem = pager.currentItem + 1 }
		imageButtonPrevious.setOnClickListener { pager.currentItem = pager.currentItem - 1 }
	}

	private fun createTempDataSource(imageUris: List<Uri>) {
		Single.create<List<ImageInfo>> { emitter ->
			emitter.onSuccess(imageUris.map {
				val imageInfo = ImageInfo()
				imageInfo.uri = it.toString()
				MetaUtil.readMetadata(this, imageInfo)
			})
		}
			.subscribeOn(Schedulers.from(AppExecutors.DISK))
			.observeOn(AndroidSchedulers.mainThread())
			.subscribeBy {
				viewerAdapter.submitList(
					PagedList.Builder<Int, ImageInfo>(TempViewerDataSource(it), 1)
						.setFetchExecutor(AppExecutors.DISK)
						.setNotifyExecutor(AppExecutors.MAIN)
						.build())
			}
	}

	override fun onBackPressed() {
		val data = Intent()
		data.putExtra(GalleryActivity.GALLERY_INDEX_EXTRA, pager.currentItem)
		setResult(RESULT_OK, data)
		super.onBackPressed()
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.viewer_options, menu)
		return true
	}

	override fun onPrepareOptionsMenu(menu: Menu): Boolean {
		autoHide.cancel()
		return super.onPrepareOptionsMenu(menu)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		// Handle item selection
		return when (item.itemId) {
			R.id.view_edit -> {
				editImage()
				true
			}
			R.id.view_wallpaper -> {
				setWallpaper()
				true
			}
			else -> super.onOptionsItemSelected(item)
		}
	}

	private fun editImage() {
		val media = currentImage

		val format = PreferenceManager.getDefaultSharedPreferences(this).getString(
				FullSettingsActivity.KEY_EditFormat,
				resources.getStringArray(R.array.shareFormats)[0])

		val intent = Intent(Intent.ACTION_EDIT)
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

		if ("JPG" == format) {
			intent.setDataAndType(SwapProvider.createSwapUri(this, Uri.parse(media?.uri)), "image/jpeg")
		} else if ("RAW" == format) {
			intent.setDataAndType(Uri.parse(media?.uri), "image/*")
		}

		val chooser = Intent.createChooser(intent, resources.getString(R.string.editWith))
		startActivity(chooser)
	}

	private fun setWallpaper() {
		try {
			WallpaperManager.getInstance(this).setBitmap(ImageUtil.createBitmapToSize(
					ImageUtil.getThumb(this, Uri.parse(currentImage?.uri)), displayWidth, displayHeight))
		} catch (e: Exception) {
			Log.e(ViewerActivity.TAG, e.toString())
			Toast.makeText(this@ViewerActivity, R.string.resultWallpaperFailed, Toast.LENGTH_SHORT).show()
		}

	}

	override fun setMaxProgress(max: Int) {}    //TODO: Clearly progress no longer belongs in core activity

	override fun incrementProgress() {}

	override fun endProgress() {}

	private inner class AutoHideMetaTask : TimerTask() {
		override fun run() {
			viewModel.hideInterface()
		}
	}

	companion object {
		private val TAG = ViewerActivity::class.java.simpleName
		const val EXTRA_START_INDEX = "viewer_index"
		const val EXTRA_FILTER = "viewer_filter"
		private val tempFolder = FolderEntity("tempFolder", true, true, -1)
	}
}