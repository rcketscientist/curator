package com.anthonymandra.framework

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentProviderOperation
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnCancelListener
import android.content.Intent
import android.net.Uri
import android.os.*
import android.preference.PreferenceManager
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.anthonymandra.image.ImageConfiguration
import com.anthonymandra.image.JpegConfiguration
import com.anthonymandra.image.TiffConfiguration
import com.anthonymandra.imageprocessor.ImageProcessor
import com.anthonymandra.rawdroid.*
import com.anthonymandra.rawdroid.BuildConfig
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.MetadataTest
import com.anthonymandra.rawdroid.ui.CoreViewModel
import com.anthonymandra.util.AppExecutors
import com.anthonymandra.util.FileUtil
import com.crashlytics.android.Crashlytics
import com.google.android.material.snackbar.Snackbar
import com.inscription.ChangeLogDialog
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.*

abstract class CoreActivity : DocumentActivity() {

    protected lateinit var recycleBin: DocumentRecycleBin
    private lateinit var mSwapDir: File

    protected lateinit var xmpEditFragment: XmpEditFragment

    /**
     * Stores uris when lifecycle is interrupted (ie: requesting a destination folder)
     */
    //TODO: Pretty sure this isn't needed...
    protected var mItemsForIntent = longArrayOf()

    private lateinit var licenseHandler: LicenseHandler
    protected abstract val selectedIds: LongArray

    lateinit var notificationManager: NotificationManager

    /**
     * Subclasses must define the layout id here.  It will be loaded in [.onCreate].
     * The layout should conform to viewer template (xmp, meta, histogram, etc).
     * @return The resource id of the layout to load
     */
    protected abstract val contentView: Int

    protected val dataRepo by lazy { (application as App).dataRepo }

    /**
     * @return The root view for this activity.
     */
    val rootView: View
        get() = findViewById(android.R.id.content)


    private enum class WriteActions {
        COPY,
        COPY_THUMB,
        DELETE,
        SAVE_IMAGE,
        RECYCLE,
        RESTORE,
        RENAME,
        WRITE_XMP
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(contentView)
        setStoragePermissionRequestEnabled(true)

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
        createSwapDir()
        createRecycleBin()
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
        clearSwapDir()
        recycleBin.closeCache()
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

    override fun onResumeWriteAction(callingMethod: Enum<*>?, callingParameters: Array<Any>) {
        if (callingMethod == null) {
            Crashlytics.logException(Exception("Null Write Method"))
            return
        }

        if (callingMethod is WriteActions) {
            when (callingMethod) {
//                CoreActivity.WriteActions.COPY ->
//                    dataRepo.images(callingParameters[0] as List<String>).value?.let {
//                        copyImages(it, callingParameters[1] as Uri)
//                    }

                CoreActivity.WriteActions.RECYCLE -> RecycleTask().execute(*callingParameters)
                CoreActivity.WriteActions.RESTORE -> RestoreTask().execute(*callingParameters)
//                CoreActivity.WriteActions.WRITE_XMP -> WriteXmpTask().execute(*callingParameters)
            }
        }
        clearWriteResume()
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

    /**
     * Create swap directory or clear the contents
     */
    private fun createSwapDir() {
        Completable.fromAction {
            mSwapDir = FileUtil.getDiskCacheDir(this, SWAP_BIN_DIR)
            if (!mSwapDir.exists()) {
                mSwapDir.mkdirs()
            }
        }.subscribeOn(Schedulers.from(AppExecutors.DISK))
        .subscribeBy(
            // TODO:
            onComplete = {},
            onError = {}
        )
    }

    private fun clearSwapDir() {
        Observable.fromIterable(mSwapDir.listFiles().asList())
            .subscribeOn(Schedulers.from(AppExecutors.DISK))
            .subscribeBy(
                //TODO:
                onError = {},
                onComplete = {},
                onNext = { it.delete() }
            )
    }

    private fun createRecycleBin() {
        val settings = PreferenceManager.getDefaultSharedPreferences(this)

        val useRecycle = settings.getBoolean(FullSettingsActivity.KEY_UseRecycleBin, true)
        val binSizeMb: Int = try {
            PreferenceManager.getDefaultSharedPreferences(this).getInt(
                FullSettingsActivity.KEY_RecycleBinSize,
                FullSettingsActivity.defRecycleBin)
        } catch (e: NumberFormatException) {
            0
        }

        if (useRecycle) {
            recycleBin = DocumentRecycleBin(this, RECYCLE_BIN_DIR, binSizeMb * 1024 * 1024)
        }
    }

    private fun showRecycleBin() {
        val settings = PreferenceManager.getDefaultSharedPreferences(this)
        val useRecycle = settings.getBoolean(FullSettingsActivity.KEY_DeleteConfirmation, true)

        if (!useRecycle) return

        val keys = recycleBin.keys
        val filesToRestore = ArrayList<String>()
        val shortNames = ArrayList<String>(keys.size)

        keys.mapNotNullTo(shortNames) { Uri.parse(it).lastPathSegment }

        AlertDialog.Builder(this).setTitle(R.string.recycleBin)
            .setNegativeButton(R.string.emptyRecycleBin) { _, _ -> recycleBin.clearCache() }
            .setNeutralButton(R.string.neutral) { _, _ ->  } // cancel, do nothing
            .setPositiveButton(R.string.restoreFile) { _, _ ->
                if (!filesToRestore.isEmpty())
                    restoreFiles(filesToRestore)
            }
            .setMultiChoiceItems(shortNames.toTypedArray(), null) { _, which, isChecked ->
                if (isChecked)
                    filesToRestore.add(keys[which])
                else
                    filesToRestore.remove(keys[which])
            }
                .show()
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

        viewModel.images(itemsToDelete).subscribeBy { images ->
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
                RecycleTask().execute(itemsToDelete)
            }
        }
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

        viewModel.images(selection).subscribeBy {selectedImages ->
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
        }

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

    fun saveImages(images: Collection<MetadataTest>, destinationFolder: Uri, config: ImageConfiguration) {
        if (images.isEmpty()) return

        var insert = false
        val insertions = arrayListOf<MetadataTest>()
        AlertDialog.Builder(this)
            .setMessage("Add converted images to the library?")
            .setPositiveButton(R.string.positive) { _, _ -> insert = true }
            .setNegativeButton(R.string.negative) { _, _ -> /*dismiss*/ }.show()

        var progress = 0
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
        builder.setContentTitle(getString(R.string.savingImages))
            .setPriority(IMPORTANT_NOTIFICATION)
            .setVibrate(longArrayOf(0, 0)) // We don't ask for permission, this allows peek
            .setProgress(images.size, progress, false)
            .setSmallIcon(R.mipmap.ic_launcher)

        notificationManager.notify(0, builder.build())

        Observable.fromIterable(images)
            .subscribeOn(Schedulers.from(AppExecutors.DISK))
            .map {
                val source = UsefulDocumentFile.fromUri(this, Uri.parse(it.uri))
                val desiredName = FileUtil.swapExtention(source.name, config.extension)
                val desiredUri = DocumentUtil.getChildUri(destinationFolder, desiredName)
                val destinationTree = getDocumentFile(destinationFolder, true, true)

                var destinationFile = UsefulDocumentFile.fromUri(this, desiredUri)
                if (!destinationFile.exists())
                    destinationFile = destinationTree.createFile(null, desiredName)

                val inputPfd = FileUtil.getParcelFileDescriptor(this, source.uri, "r")
                val outputPfd = FileUtil.getParcelFileDescriptor(this, destinationFile.uri, "w")

                when (config.type) {
                    ImageConfiguration.ImageType.jpeg -> {
                        val quality = (config as JpegConfiguration).quality
//                        if (wm != null) {
//                            ImageProcessor.writeThumb(inputPfd.fd, quality,
//                                outputPfd.fd, wm.watermark, wm.margins.array,
//                                wm.waterWidth, wm.waterHeight) && success
//                        } else {
                        ImageProcessor.writeThumb(inputPfd.fd, quality, outputPfd.fd)
//                        }
                    }
                    ImageConfiguration.ImageType.tiff -> {
                        val compress = (config as TiffConfiguration).compress
//                        if (wm != null) {
//                            success = ImageProcessor.writeTiff(desiredName, inputPfd.fd,
//                                outputPfd.fd, compress, wm.watermark, wm.margins.array,
//                                wm.waterWidth, wm.waterHeight) && success
//                        } else {
                        ImageProcessor.writeTiff(desiredName, inputPfd.fd, outputPfd.fd, compress)
//                        }
                    }
                    else -> throw UnsupportedOperationException("unimplemented save type.")
                }

                builder.setProgress(images.size, progress, false)
                notificationManager.notify(0, builder.build())

                destinationFile.uri
            }
            .observeOn(Schedulers.from(AppExecutors.MAIN))
            .subscribeBy(
                onNext = {
                    builder.setProgress(images.size, ++progress, false)
                    builder.setContentText(it.toString())
                    notificationManager.notify(0, builder.build())
                    incrementProgress()

                    val insertion = MetadataTest()
                    insertion.uri = it.toString()
                    insertions.add(insertion)
                },
                onComplete = {
                    endProgress()

                    // When the loop is finished, updates the notification
                    builder.setContentText("Complete")
                        .setProgress(0,0,false) // Removes the progress bar
                    notificationManager.notify(0, builder.build())

                    if (insert) {
                        dataRepo.insertMeta(*insertions.toTypedArray())
                    }
                },
                onError = {
                    it.printStackTrace()
                    Crashlytics.logException(it)
                    builder.setContentText("Some images did not transfer")
                }
            )
    }

    // TODO: Need to look into way to make this an application singleton (ProcessLifecycleOwner?)
    protected inner class RecycleTask : AsyncTask<Any, Int, Void>(), OnCancelListener {
        override fun onPreExecute() {
//            mProgressDialog!!.setTitle(R.string.recyclingFiles)
//            mProgressDialog!!.show()
        }

        override fun doInBackground(vararg params: Any): Void? {
            if (params[0] !is List<*> || (params[0] as List<*>)[0] !is Uri)
                throw IllegalArgumentException()

            // Create a copy to keep track of completed deletions in case this needs to be restarted
            // to request write permission
            val totalImages = params[0] as List<Uri>
            val remainingImages = ArrayList(totalImages)

//            mProgressDialog!!.max = remainingImages.size

            val dbDeletes = ArrayList<ContentProviderOperation>()
            for (toRecycle in totalImages) {
                setWriteResume(WriteActions.RECYCLE, arrayOf<Any>(remainingImages))
                try {
                    //TODO: Handle related files
                    recycleBin.addFile(toRecycle)
                    // TODO: FIXME
//                    dbDeletes.add(MetaUtil.newDelete(toRecycle))
                } catch (e: DocumentActivity.WritePermissionException) {
                    e.printStackTrace()
                    // We'll be automatically requesting write permission so kill this process
                    return null
                } catch (e: Exception) {
                    Crashlytics.setString("uri", toRecycle.toString())
                    Crashlytics.log(e.toString())
                }

                remainingImages.remove(toRecycle)
            }
            return null
        }

        override fun onPostExecute(result: Void) {
//            if (!this@CoreActivity.isDestroyed && mProgressDialog != null)
//                mProgressDialog!!.dismiss()
        }

        override fun onCancelled() {
        }

        override fun onCancel(dialog: DialogInterface) {
            this.cancel(true)
        }
    }

    private fun restoreFiles(toRestore: List<String>) {
        RestoreTask().execute(toRestore)
    }

    protected inner class RestoreTask : AsyncTask<Any, Int, Boolean>() {
        override fun doInBackground(vararg params: Any): Boolean? {
            if (params.isEmpty())
                return false
            if (params[0] !is List<*>)
                throw IllegalArgumentException()

            val totalImages = params[0] as List<String>
            if (totalImages.isEmpty())
                return false

            // Create a copy to keep track of completed deletions in case this needs to be restarted
            // to request write permission

            val remainingImages = ArrayList(totalImages)

            var totalSuccess = true
            val dbInserts = ArrayList<ContentProviderOperation>()
            for (image in totalImages) {
                val toRestore = Uri.parse(image)
                try {
                    setWriteResume(WriteActions.RESTORE, arrayOf<Any>(remainingImages))
                    val recycledFile = recycleBin.getFile(image) ?: continue

                    val uri = Uri.fromFile(recycledFile)
                    moveFile(uri, toRestore)
                } catch (e: DocumentActivity.WritePermissionException) {
                    e.printStackTrace()
                    totalSuccess = false
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }
            // TODO: FIXME
//            MetaUtil.updateMetaDatabase(this@CoreActivity, dbInserts)
            return totalSuccess
        }

        override fun onPostExecute(result: Boolean?) {
            if (result!!)
                clearWriteResume()
            else
                Snackbar.make(rootView, R.string.restoreFail, Snackbar.LENGTH_LONG).show()
        }
    }

    protected abstract val viewModel: CoreViewModel
    abstract fun setMaxProgress(max: Int)
    abstract fun incrementProgress()
    abstract fun endProgress()

    companion object {
        const val NOTIFICATION_CHANNEL = "notifications"

        const val SWAP_BIN_DIR = "swap"
        const val RECYCLE_BIN_DIR = "recycle"

        private const val REQUEST_SAVE_AS_DIR = 15
        private const val EXPIRATION = 5184000000L //~60 days

        val IMPORTANT_NOTIFICATION =
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N) {
                NotificationManager.IMPORTANCE_HIGH
            } else {
                Notification.PRIORITY_HIGH
            }
    }
}
