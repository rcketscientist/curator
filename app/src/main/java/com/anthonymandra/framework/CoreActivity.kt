package com.anthonymandra.framework

import android.Manifest
import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.UriPermission
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.preference.PreferenceManager
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import com.anthonymandra.image.ImageConfiguration
import com.anthonymandra.rawdroid.*
import com.anthonymandra.rawdroid.BuildConfig
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.ui.CoreViewModel
import com.anthonymandra.util.AppExecutors
import com.anthonymandra.util.FileUtil
import com.google.android.material.snackbar.Snackbar
import com.inscription.ChangeLogDialog
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.lang.ref.WeakReference
import java.util.*

abstract class CoreActivity : AppCompatActivity() {

	private lateinit var mSwapDir: File
	private lateinit var licenseHandler: LicenseHandler
	protected lateinit var xmpEditFragment: XmpEditFragment
	lateinit var notificationManager: NotificationManager
	protected val compositeDisposable = CompositeDisposable()

	private val recycleBin: RecycleBin by lazy {
		val binSizeMb: Int = try {
			PreferenceManager.getDefaultSharedPreferences(this).getInt(
				FullSettingsActivity.KEY_RecycleBinSize,
				FullSettingsActivity.defRecycleBin)
		} catch (e: NumberFormatException) {
			FullSettingsActivity.defRecycleBin
		}
		RecycleBin.getInstance(this, binSizeMb * 1024 * 1024L)
	}
	protected val dataRepo by lazy { (application as App).dataRepo }
	protected val rootPermissions: List<UriPermission> by lazy { contentResolver.persistedUriPermissions }

	/**
	 * Stores uris when lifecycle is interrupted (ie: requesting a destination folder)
	 */
	//TODO: Pretty sure this isn't needed...
	protected var mItemsForIntent = longArrayOf()

	protected abstract val selectedIds: LongArray


	/**
	 * Subclasses must define the layout id here.  It will be loaded in [.onCreate].
	 * The layout should conform to viewer template (xmp, meta, histogram, etc).
	 * @return The resource id of the layout to load
	 */
	protected abstract val contentView: Int

	/**
	 * @return The root view for this activity.
	 */
	val rootView: View
		get() = findViewById(android.R.id.content)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(contentView)

		licenseHandler = CoreActivity.LicenseHandler(this.applicationContext)

		notificationManager = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		createIoChannel()

		if ("beta" == BuildConfig.FLAVOR_cycle && BuildConfig.BUILD_TIME + EXPIRATION < System.currentTimeMillis()) {
			Toast.makeText(this, "Beta has expired.", Toast.LENGTH_LONG).show()
			//TODO: Add link to Curator store page
			finish()
		}

		PreferenceManager.setDefaultValues(this, R.xml.preferences_metadata, false)
		PreferenceManager.setDefaultValues(this, R.xml.preferences_storage, false)
		PreferenceManager.setDefaultValues(this, R.xml.preferences_view, false)
		PreferenceManager.setDefaultValues(this, R.xml.preferences_license, false)
		PreferenceManager.setDefaultValues(this, R.xml.preferences_watermark, false)

		findViewById<View>(R.id.xmpSidebarButton).setOnClickListener { toggleEditXmpFragment() }
	}

	override fun onResume() {
		super.onResume()

		Completable.fromAction {
			mSwapDir = FileUtil.getDiskCacheDir(this, SWAP_BIN_DIR)
			if (!mSwapDir.exists()) {
				mSwapDir.mkdirs()
			}
		}
			.subscribeOn(Schedulers.from(AppExecutors.DISK))
			.subscribeBy(
				// TODO:
				onComplete = {},
				onError = {}
			).addTo(compositeDisposable)

		val needsRead = ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
		val needsWrite = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

		if (needsRead || needsWrite)
			requestStoragePermission()
	}

	override fun onPause() {
		super.onPause()
		recycleBin.flushCache()
	}

	override fun onBackPressed() {
		if (!xmpEditFragment.isHidden) {
			hideEditXmpFragment()
			return
		}
		super.onBackPressed()
	}

	override fun onDestroy() {
		super.onDestroy()

		if (::mSwapDir.isInitialized) {
			// Is this main thread I/O an issue?
			mSwapDir.listFiles().forEach {
				it.delete()
			}
		}
		recycleBin.closeCache()
		compositeDisposable.dispose()
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		// Handle item selection
		when (item.itemId) {
			R.id.contact -> {
				requestEmailIntent()
				return true
			}
			R.id.about -> {
				val changeLogDialog = ChangeLogDialog(this)
				changeLogDialog.show(false)
				return true
			}
			R.id.contextSaveAs -> {
				storeSelectionForIntent()
				requestSaveAsDestination()
				return true
			}
			R.id.menu_delete -> {
				deleteImages(selectedIds)
				return true
			}
			R.id.menu_recycle -> {
				showRecycleBin()
				return true
			}
			R.id.settings -> {
				requestSettings()
				return true
			}
			R.id.menu_share -> {
				requestShare()
				return true
			}
			else -> return super.onOptionsItemSelected(item)
		}
	}

	/**
	 * Requests the ability to read or write to external storage.
	 */
	private fun requestStoragePermission() {
		val justifyWrite = ActivityCompat.shouldShowRequestPermissionRationale(this,
			Manifest.permission.WRITE_EXTERNAL_STORAGE)
		val justifyRead = ActivityCompat.shouldShowRequestPermissionRationale(this,
			Manifest.permission.READ_EXTERNAL_STORAGE)

		// Storage permission has been requested and denied, show a Snackbar with the option to
		// grant permission
		if (justifyRead || justifyWrite) {
			// Provide an additional rationale to the user if the permission was not granted
			// and the user would benefit from additional context for the use of the permission.
			Snackbar.make(rootView, R.string.permissionStorageRationale, Snackbar.LENGTH_INDEFINITE)
				.setAction(com.anthonymandra.framework.R.string.ok) {
					ActivityCompat.requestPermissions(this,
						PERMISSIONS_STORAGE, REQUEST_STORAGE_PERMISSION)
				}
				.show()
		} else {
			// Storage permission has not been requested yet. Request for first time.
			ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, REQUEST_STORAGE_PERMISSION)
		}
	}

	protected fun requestWritePermission(requestCode: Int) {
		runOnUiThread {
			val image = ImageView(this)
			image.setImageDrawable(getDrawable(com.anthonymandra.framework.R.drawable.document_api_guide))
			val builder = androidx.appcompat.app.AlertDialog.Builder(this)
				.setTitle(com.anthonymandra.framework.R.string.dialogWriteRequestTitle)
				.setView(image)
			val dialog = builder.create()
			image.setOnClickListener {
				val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
//					intent.putExtra(DocumentsContract.EXTRA_PROMPT, getString(com.anthonymandra.framework.R.string.allowWrite))
				startActivityForResult(intent, requestCode)
				dialog.dismiss()
			}
			dialog.show()
		}
	}

	@TargetApi(Build.VERSION_CODES.O)
	private fun createIoChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(NOTIFICATION_CHANNEL,
				getString(R.string.io_channel_name),
				NotificationManager.IMPORTANCE_HIGH)
			channel.description = getString(R.string.io_channel_desc)
			channel.enableVibration(false)
			notificationManager.createNotificationChannel(channel)
		}
	}

	protected fun storeSelectionForIntent() {
		mItemsForIntent = selectedIds
	}

	public override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
		super.onActivityResult(requestCode, resultCode, intent)

		when (requestCode) {
			REQUEST_SAVE_AS_DIR -> if (resultCode == RESULT_OK && intent != null) {
				intent.data?.let {
					handleSaveDestinationResult(it)
				}
			}
		}
	}

	private fun handleSaveDestinationResult(destination: Uri) {
		// Load default save config if it exists and automatically apply it
		val config = ImageConfiguration.fromPreference(this)
		var insert = false

		if (config != null) {
			// TODO: This alert could be a checkbox in the save dialog
			AlertDialog.Builder(this)
				.setMessage("Add converted images to the library?")
				.setPositiveButton(R.string.positive) { _, _ -> insert = true }
				.setNegativeButton(R.string.negative) { _, _ -> /*dismiss*/ }.show()
			viewModel.startSaveWorker(mItemsForIntent, destination, config, insert)
			return
		}

		val dialog = SaveConfigDialog(this)
		dialog.setSaveConfigurationListener { imageConfig ->
			// TODO: This alert could be a checkbox in the save dialog
			AlertDialog.Builder(this)
				.setMessage("Add converted images to the library?")
				.setPositiveButton(R.string.positive) { _, _ -> insert = true }
				.setNegativeButton(R.string.negative) { _, _ -> /*dismiss*/ }.show()
			viewModel.startSaveWorker(mItemsForIntent, destination, imageConfig, insert)
		}
		dialog.show()
	}

	override fun onPostCreate(savedInstanceState: Bundle?) {
		super.onPostCreate(savedInstanceState)
		xmpEditFragment = supportFragmentManager.findFragmentById(R.id.editFragment) as XmpEditFragment
		xmpEditFragment.setListener { xmp ->
			viewModel.startMetaWriterWorker(selectedIds, xmp, XmpUpdateField.All)
		}

		xmpEditFragment.setLabelListener { label ->
			viewModel.startMetaWriterWorker(
				selectedIds,
				XmpValues(label = label),
				XmpUpdateField.Label)
		}

		xmpEditFragment.setRatingListener { rating ->
			viewModel.startMetaWriterWorker(
				selectedIds,
				XmpValues(rating = rating),
				XmpUpdateField.Rating)
		}
		xmpEditFragment.setSubjectListener { subject ->
			viewModel.startMetaWriterWorker(
				selectedIds,
				XmpValues(subject = subject.orEmpty().toList()),
				XmpUpdateField.Subject)
		}
		hideEditXmpFragment()
	}

	private fun toggleEditXmpFragment() {
		if (xmpEditFragment.isHidden) {
			showEditXmpFragment()
		} else {
			hideEditXmpFragment()
		}
	}

	private fun hideEditXmpFragment() {
		val ft = supportFragmentManager.beginTransaction()
		ft.hide(xmpEditFragment)
		ft.setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
		ft.commit()
	}

	private fun showEditXmpFragment() {
		val ft = supportFragmentManager.beginTransaction()
		ft.show(xmpEditFragment)
		ft.setCustomAnimations(android.R.anim.slide_out_right, android.R.anim.slide_in_left, android.R.anim.slide_out_right, android.R.anim.slide_in_left)
		ft.commit()
	}

	private fun showRecycleBin() {
		val settings = PreferenceManager.getDefaultSharedPreferences(this)
		val useRecycle = settings.getBoolean(FullSettingsActivity.KEY_DeleteConfirmation, true)

		if (!useRecycle) return

		viewModel.recycledImages(recycleBin.keys.toLongArray())
			.observeOn(AndroidSchedulers.mainThread())
			.subscribeBy { images ->
				val filesToRestore = ArrayList<Long>()
				val shortNames = images.mapNotNull { Uri.parse(it.path).lastPathSegment }

				AlertDialog.Builder(this).setTitle(R.string.recycleBin)
					.setNegativeButton(R.string.emptyRecycleBin) { _, _ -> recycleBin.clearCache() }
					.setNeutralButton(R.string.neutral) { _, _ -> } // cancel, do nothing
					.setMultiChoiceItems(shortNames.toTypedArray(), null) { _, which, isChecked ->
						if (isChecked)
							filesToRestore.add(images[which].id)
						else
							filesToRestore.remove(images[which].id)
					}
					.setPositiveButton(R.string.restoreFile) { _, _ ->
						if (!filesToRestore.isEmpty()) {
							viewModel.startRestoreWorker(filesToRestore.toTypedArray())
						}
					}
					.show()
			}.addTo(compositeDisposable)
	}

	private fun deleteImages(itemsToDelete: LongArray) {
		if (itemsToDelete.isEmpty()) {
			Toast.makeText(baseContext, R.string.warningNoItemsSelected, Toast.LENGTH_SHORT).show()
			return
		}

		val settings = PreferenceManager.getDefaultSharedPreferences(this)
		val deleteConfirm = settings.getBoolean(FullSettingsActivity.KEY_DeleteConfirmation, true)
		val useRecycle = settings.getBoolean(FullSettingsActivity.KEY_UseRecycleBin, true)
		val justDelete: Boolean?
		val message: String

		// Go straight to delete if
		// 1. MTP (unsupported)
		// 2. Recycle is set to off
		// 3. For some reason the bin is null
		if (!useRecycle) {
			justDelete = true
			message = getString(R.string.warningDeleteDirect)
		} else {
			justDelete = false
			message = getString(R.string.warningDeleteExceedsRecycle)
		}

		viewModel.images(itemsToDelete)
			.observeOn(AndroidSchedulers.mainThread())
			.subscribeBy { images ->
				val spaceRequired: Long = images
					.asSequence()
					.filterNotNull()
					.map { it.size }
					.sum()

				if (justDelete || recycleBin.binSize < spaceRequired) {
					if (deleteConfirm) {
						AlertDialog.Builder(this).setTitle(R.string.prefTitleDeleteConfirmation).setMessage(message)
							.setNeutralButton(R.string.neutral) { _, _ -> } // do nothing
							.setPositiveButton(R.string.delete) { _, _ -> viewModel.startDeleteWorker(itemsToDelete) }
							.show()
					} else {
						viewModel.startDeleteWorker(itemsToDelete)
					}
				} else {
					viewModel.startRecycleWorker(images.map { it.id }.toLongArray())
				}
			}.addTo(compositeDisposable)
	}

	private fun requestEmailIntent() {
		requestEmailIntent(null)
	}

	protected fun requestEmailIntent(subject: String?) {
		val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.fromParts(
			"mailto", "rawdroid@anthonymandra.com", null))

		if (subject != null) {
			emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject)
		}

		val body = "Variant:   " + BuildConfig.FLAVOR + "\n" +
			"Version:   " + BuildConfig.VERSION_NAME + "\n" +
			"Make:      " + Build.MANUFACTURER + "\n" +
			"Model:     " + Build.MODEL + "\n" +
			"ABI:       " + Arrays.toString(Build.SUPPORTED_ABIS) + "\n" +
			"Android:   " + Build.DISPLAY + "\n" +
			"SDK:       " + Build.VERSION.SDK_INT + "\n\n" +
			"---Please don't remove this data---" + "\n\n"

		emailIntent.putExtra(Intent.EXTRA_TEXT, body)
		startActivity(Intent.createChooser(emailIntent, "Send email..."))
	}

	private fun requestSettings() {
		val settings = Intent(this, FullSettingsActivity::class.java)
		startActivity(settings)
	}

	private fun requestSaveAsDestination() {
		val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
		startActivityForResult(intent, REQUEST_SAVE_AS_DIR)
	}

	private fun requestShare() {
		if (selectedIds.isEmpty()) {
			Snackbar.make(rootView, R.string.warningNoItemsSelected, Snackbar.LENGTH_SHORT).show()
			return
		}

		val format = PreferenceManager.getDefaultSharedPreferences(this).getString(
			FullSettingsActivity.KEY_ShareFormat,
			resources.getStringArray(R.array.shareFormats)[0])

		val intent = Intent()
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

		var convert = true
		if ("JPG" == format) {
			intent.type = "image/jpeg"
			convert = true
		} else if ("RAW" == format) {
			intent.type = "image/*"
			convert = false
		}

		// We need to limit the number of shares to avoid TransactionTooLargeException
		// TODO: If swap took IDs can we remove share limit?
		val limitImages = selectedIds.size > 10
		if (limitImages) {
			val shareLimit = Toast.makeText(this, R.string.shareSubset, Toast.LENGTH_LONG)
			shareLimit.setGravity(Gravity.CENTER, 0, 0)
			shareLimit.show()
		}
		val selection = if (limitImages) selectedIds.sliceArray(0..9) else selectedIds

		viewModel.images(selection).subscribeBy { selectedImages ->
			if (selectedImages.size > 1) {
				intent.action = Intent.ACTION_SEND_MULTIPLE

				intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM,
					ArrayList(selectedImages.map {
						if (convert)
							SwapProvider.createSwapUri(this, it.uri.toUri())
						else
							it.uri.toUri()
					})
				)
			} else {
				intent.action = Intent.ACTION_SEND
				val uri = Uri.parse(selectedImages[0].uri)
				if (convert)
					intent.putExtra(Intent.EXTRA_STREAM, SwapProvider.createSwapUri(this, uri))
				else
					intent.putExtra(Intent.EXTRA_STREAM, uri)
			}
		}.addTo(compositeDisposable)

		startActivity(Intent.createChooser(intent, getString(R.string.share)))
	}

	open class LicenseHandler(context: Context) : Handler() {
		private val mContext: WeakReference<Context> = WeakReference(context)

		override fun handleMessage(msg: Message) {
			val state = msg.data.getSerializable(License.KEY_LICENSE_RESPONSE)
			if (state == null || state.toString().startsWith("modified")) {
				for (i in 0..2) //TODO: FIXME
					Toast.makeText(mContext.get(), "An app on your device has attempted to modify Rawdroid.  Check Settings > License for more information.", Toast.LENGTH_LONG).show()
			} else if (state == License.LicenseState.error) {
				Toast.makeText(mContext.get(), "There was an error communicating with Google Play.  Check Settings > License for more information.", Toast.LENGTH_LONG).show()
			}
		}
	}

	protected abstract val viewModel: CoreViewModel
	abstract fun setMaxProgress(max: Int)
	abstract fun incrementProgress()
	abstract fun endProgress()

	companion object {
		const val NOTIFICATION_CHANNEL = "notifications"

		const val SWAP_BIN_DIR = "swap"

		private const val REQUEST_STORAGE_PERMISSION = 0
		private const val REQUEST_SAVE_AS_DIR = 15

		private const val EXPIRATION = 5184000000L //~60 days

		/**
		 * Permissions required to read and write to storage.
		 */
		private val PERMISSIONS_STORAGE = arrayOf(
			Manifest.permission.READ_EXTERNAL_STORAGE,
			Manifest.permission.WRITE_EXTERNAL_STORAGE
		)
	}
}
