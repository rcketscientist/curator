package com.anthonymandra.framework

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.*
import android.content.DialogInterface.OnCancelListener
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
import android.support.v4.app.NotificationCompat
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.*
import com.anthonymandra.content.Meta
import com.anthonymandra.image.ImageConfiguration
import com.anthonymandra.image.JpegConfiguration
import com.anthonymandra.image.TiffConfiguration
import com.anthonymandra.imageprocessor.ImageProcessor
import com.anthonymandra.rawdroid.*
import com.anthonymandra.rawdroid.BuildConfig
import com.anthonymandra.rawdroid.R
import com.anthonymandra.rawdroid.data.MetadataTest
import com.anthonymandra.util.*
import com.anthonymandra.util.FileUtil
import com.crashlytics.android.Crashlytics
import com.inscription.ChangeLogDialog
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
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

    protected var mActivityVisible: Boolean = false

    /**
     * Stores uris when lifecycle is interrupted (ie: requesting a destination folder)
     */
    protected var mItemsForIntent = mutableListOf<MetadataTest>()

    private lateinit var licenseHandler: LicenseHandler
    protected abstract val selectedImages: Collection<MetadataTest>

    private lateinit var notificationManager: NotificationManager

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
        mActivityVisible = true
        LicenseManager.getLicense(this, licenseHandler)
        createSwapDir()
        createRecycleBin()
    }

    override fun onPause() {
        super.onPause()
        mActivityVisible = false
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
                changeLogDialog.show(Constants.VariantCode == 8)
                return true
            }
            R.id.contextSaveAs -> {
                storeSelectionForIntent()
                requestSaveAsDestination()
                return true
            }
            R.id.menu_delete -> {
                deleteImages(selectedImages)
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

    private fun getHighPriorityNotification() : Int {
        return if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N) {
            NotificationManager.IMPORTANCE_HIGH
        } else {
            Notification.PRIORITY_HIGH
        }
    }

    protected fun storeSelectionForIntent() {
        mItemsForIntent.clear()
        mItemsForIntent.addAll(selectedImages)
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_SAVE_AS_DIR -> if (resultCode == RESULT_OK && data != null) {
                handleSaveDestinationResult(data.data)
            }
        }
    }

    private fun handleSaveDestinationResult(destination: Uri) {
        //		storeSelectionForIntent();	// dialog resets CAB, so store first

        // TODO: Might want to figure out a way to get free space to introduce this check again
        //        long importSize = getSelectedImageSize();
        //        if (destination.getFreeSpace() < importSize)
        //        {
        //            Toast.makeText(this, R.string.warningNotEnoughSpace, Toast.LENGTH_LONG).show();
        //            return;
        //        }

        // Load default save config if it exists and automatically apply it
        val config = ImageConfiguration.loadPreference(this)
        if (config != null) {
            saveTask(mItemsForIntent, destination, config)
            return
        }

        val dialog = SaveConfigDialog(this)
        dialog.setSaveConfigurationListener { saveTask(mItemsForIntent, destination, it) }
        dialog.show()
    }

    override fun onResumeWriteAction(callingMethod: Enum<*>?, callingParameters: Array<Any>) {
        if (callingMethod == null) {
            Crashlytics.logException(Exception("Null Write Method"))
            return
        }

        if (callingMethod is WriteActions) {
            when (callingMethod) {
                CoreActivity.WriteActions.COPY ->
                    dataRepo.images(callingParameters[0] as List<String>).value?.let {
                        copyImages(it, callingParameters[1] as Uri)
                    }

                CoreActivity.WriteActions.DELETE -> deleteTask(callingParameters[0] as Collection<Uri>)
                CoreActivity.WriteActions.RECYCLE -> RecycleTask().execute(*callingParameters)
                CoreActivity.WriteActions.RESTORE -> RestoreTask().execute(*callingParameters)
                CoreActivity.WriteActions.WRITE_XMP -> WriteXmpTask().execute(*callingParameters)
            }
        }
        clearWriteResume()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        xmpEditFragment = supportFragmentManager.findFragmentById(R.id.editFragment) as XmpEditFragment
        xmpEditFragment.setListener { rating, label, subject ->
            writeXmpModifications(XmpEditFragment.XmpEditValues(rating, subject, label))
        }

        xmpEditFragment.setLabelListener { label ->
            Thread(PrepareXmpRunnable(
                XmpEditFragment.XmpEditValues(label = label),
                XmpUpdateField.Label)).start()
        }

        xmpEditFragment.setRatingListener { rating ->
            Thread(PrepareXmpRunnable(
                XmpEditFragment.XmpEditValues(rating = rating),
                XmpUpdateField.Rating)).start()
        }
        xmpEditFragment.setSubjectListener { subject ->
            Thread(PrepareXmpRunnable(
                XmpEditFragment.XmpEditValues(subject = subject),
                XmpUpdateField.Subject)).start()
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

    private fun  writeXmpModifications(values: XmpEditFragment.XmpEditValues) {
        val selection = selectedImages
        if (selection.isNotEmpty()) {
            val cv = ContentValues()
            cv.put(Meta.LABEL, values.label)
            cv.put(Meta.RATING, values.rating)
            //FIXME: This should update the subject junction!
            //			cv.put(Meta.SUBJECT, DbUtil.convertArrayToString(values.subject));

            val xmpPairing = HashMap<Uri, ContentValues>()
            for (image in selection) {
                xmpPairing[Uri.parse(image.uri)] = cv
            }

            writeXmp(xmpPairing)
        }
    }

    private fun writeXmp(xmpPairing: Map<Uri, ContentValues>) {
        WriteXmpTask().execute(xmpPairing)
    }

    /**
     * Create swap directory or clear the contents
     */
    private fun createSwapDir() {
        mSwapDir = FileUtil.getDiskCacheDir(this, SWAP_BIN_DIR)
        if (!mSwapDir.exists()) {
            mSwapDir.mkdirs()
        }
    }

    private fun clearSwapDir() {
        Thread(Runnable {
            val swapFiles = mSwapDir.listFiles()
            if (swapFiles != null) {
                for (toDelete in swapFiles) {
                    toDelete.delete()
                }
            }
        }).start()
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

        keys.mapTo(shortNames) { Uri.parse(it).lastPathSegment }

        AlertDialog.Builder(this).setTitle(R.string.recycleBin)
            .setNegativeButton(R.string.emptyRecycleBin) { _, _ -> recycleBin.clearCache() }
            .setNeutralButton(R.string.neutral) { _, _ ->  } // cancel, do nothing
            .setPositiveButton(R.string.restoreFile) { _, _ ->
                if (!filesToRestore.isEmpty())
                    restoreFiles(filesToRestore)
            }
            .setMultiChoiceItems(shortNames.toTypedArray(), null, { _, which, isChecked ->
                if (isChecked)
                    filesToRestore.add(keys[which])
                else
                    filesToRestore.remove(keys[which])
            })
            .show()
    }

    /**
     * Deletes a file and determines if a recycle is necessary.
     *
     * @param toDelete file to delete.
     */
    protected fun deleteImage(toDelete: MetadataTest) {
        val itemsToDelete = ArrayList<MetadataTest>()
        itemsToDelete.add(toDelete)
        deleteImages(itemsToDelete)
    }

    private fun deleteImages(itemsToDelete: Collection<MetadataTest>) {
        if (itemsToDelete.isEmpty()) {
            Toast.makeText(baseContext, R.string.warningNoItemsSelected, Toast.LENGTH_SHORT).show()
            return
        }

        val settings = PreferenceManager.getDefaultSharedPreferences(this)
        val deleteConfirm = settings.getBoolean(FullSettingsActivity.KEY_DeleteConfirmation, true)
        val useRecycle = settings.getBoolean(FullSettingsActivity.KEY_UseRecycleBin, true)
        val justDelete: Boolean?
        val message: String
        val spaceRequired: Long = itemsToDelete
            .asSequence()
            .filterNotNull()
            .map { File(Uri.parse(it.uri).path) }
            .filter { it.exists() }
            .map { it.length() }
            .sum()

        // Go straight to delete if
        // 1. MTP (unsupported)
        // 2. Recycle is set to off
        // 3. For some reason the bin is null
        //		if (itemsToDelete.get(0) instanceof MtpImage)
        //		{
        //			justDelete = true;
        //			message = getString(R.string.warningRecycleMtp);
        //		}
        /* else */
        if (!useRecycle) {
            justDelete = true
            message = getString(R.string.warningDeleteDirect)
        } else {
            justDelete = false
            message = getString(R.string.warningDeleteExceedsRecycle) // This message applies to deletes exceeding bin size
        }

        if (justDelete || recycleBin.binSize < spaceRequired) {
            if (deleteConfirm) {
                AlertDialog.Builder(this).setTitle(R.string.prefTitleDeleteConfirmation).setMessage(message)
                    .setNeutralButton(R.string.neutral) { _, _ -> } // do nothing
                    .setPositiveButton(R.string.delete) { _, _ -> deleteTask(itemsToDelete) }
                    .show()
            } else {
                deleteTask(itemsToDelete)
            }
        } else {
            RecycleTask().execute(itemsToDelete)
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

    /**
     * Fires after individual items are successfully added.  This will fire multiple times in a batch.
     * @param item added item
     */
    protected abstract fun onImageAdded(item: Uri)

    /**
     * Fires after individual items are successfully deleted.  This will fire multiple times in a batch.
     * @param item deleted item
     */
    protected abstract fun onImageRemoved(item: Uri)

    /**
     * Fires after all actions of a batch (or single) change to the image set are complete.
     */
    protected abstract fun onImageSetChanged()

    private fun requestShare() {
        if (selectedImages.isEmpty()) {
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

        val selectedItems = ArrayList(selectedImages)
        if (selectedItems.size > 1) {
            intent.action = Intent.ACTION_SEND_MULTIPLE
            val tooManyShares = selectedItems.size > 10

            if (tooManyShares) {
                val shareLimit = Toast.makeText(this, R.string.shareSubset, Toast.LENGTH_LONG)
                shareLimit.setGravity(Gravity.CENTER, 0, 0)
                shareLimit.show()
            }

            val share = ArrayList<Uri?>()
            // We need to limit the number of shares to avoid TransactionTooLargeException
            for (selection in if (tooManyShares) selectedItems.subList(0, 10) else selectedItems) {
                val uri = Uri.parse(selection.uri)
                if (convert)
                    share.add(SwapProvider.createSwapUri(this, uri))
                else
                    share.add(uri)
            }
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, share)
        } else {
            intent.action = Intent.ACTION_SEND
            val uri = Uri.parse(selectedItems[0].uri)
            if (convert)
                intent.putExtra(Intent.EXTRA_STREAM, SwapProvider.createSwapUri(this, uri))
            else
                intent.putExtra(Intent.EXTRA_STREAM, uri)
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

    protected abstract fun setMaxProgress(max: Int)
    protected abstract fun incrementProgress()
    protected abstract fun endProgress()
    protected abstract fun updateMessage(message: String?)

    /**
     * File operation tasks
     */

    /**
     * Copies an image and corresponding xmp and jpeg (ex: src/a.[cr2,xmp,jpg] -> dest/a.[cr2,xmp,jpg])
     * @param fromImage source image
     * @param toImage target image
     * @return success
     */
    @Throws(IOException::class)
    private fun copyAssociatedFiles(fromImage: MetadataTest, toImage: Uri): Boolean {
        val sourceUri = Uri.parse(fromImage.uri)
        if (ImageUtil.hasXmpFile(this, sourceUri)) {
            copyFile(ImageUtil.getXmpFile(this, sourceUri).uri,
                    ImageUtil.getXmpFile(this, toImage).uri)
        }
        if (ImageUtil.hasJpgFile(this, sourceUri)) {
            copyFile(ImageUtil.getJpgFile(this, sourceUri).uri,
                    ImageUtil.getJpgFile(this, toImage).uri)
        }

        fromImage.uri = toImage.toString()  // update copied uri

        return copyFile(sourceUri, toImage)
    }

    fun copyImages(images: List<Uri>, destinationFolder: Uri) {
        Single.create<List<MetadataTest>> {
                it.onSuccess(dataRepo.imagesBlocking(images.map { it.toString() }))
            }.subscribeOn(Schedulers.from(AppExecutors.DISK))
            .subscribeBy(
                onSuccess = { copyImages(it, destinationFolder)},
                onError = {}    // do nothing for now
            )
    }

    fun copyImages(images: Collection<MetadataTest>, destinationFolder: Uri) {
        // Prep things on the UI thread
        Completable.create {
                setMaxProgress(images.size)
            }
            .subscribeOn(Schedulers.from(AppExecutors.MAIN))
            .subscribe()

        var progress = 0
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
        builder.setContentTitle(getString(R.string.copyingImages))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(getHighPriorityNotification())
            .setVibrate(longArrayOf(0, 0)) // We don't ask for permission, this allows peek
            .setProgress(images.size, progress, false)
        notificationManager.notify(0, builder.build())

        Observable.fromIterable(images)
            .subscribeOn(Schedulers.from(AppExecutors.DISK))
            .map {
                val destinationFile = DocumentUtil.getChildUri(destinationFolder, it.name)
                copyAssociatedFiles(it, destinationFile)
                it.name
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onNext = {
                    builder.setProgress(images.size, ++progress, false)
                    builder.setContentText(it)
                    notificationManager.notify(0, builder.build())
                    incrementProgress()
                },
                onComplete = {
                    endProgress()
                    updateMessage(null)
                    onImageSetChanged()

                    // When the loop is finished, updates the notification
                    builder.setContentText("Complete")
                        .setProgress(0,0,false) // Removes the progress bar
                    notificationManager.notify(0, builder.build())
                },
                onError = {
                    it.printStackTrace()
//                    Crashlytics.setString("uri", toCopy.toString())
                    Crashlytics.logException(it)
                    builder.setContentText("Some images did not transfer")
                }
            )
    }

    fun saveTask(images: Collection<Uri>, destinationFolder: Uri, config: ImageConfiguration) {
        if (images.isEmpty()) return

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
        builder.setContentTitle(getString(R.string.importingImages))
            .setContentText("placeholder")
            .setSmallIcon(R.mipmap.ic_launcher)

        Completable.create {
            val remainingImages = ArrayList(images)

            val dbInserts = ArrayList<ContentProviderOperation>()
            val progress = 0
            builder.setProgress(images.size, progress, false)
            notificationManager.notify(0, builder.build())

            images.forEach { toSave ->
                setWriteResume(WriteActions.SAVE_IMAGE, arrayOf(remainingImages, destinationFolder, config))
                val source = UsefulDocumentFile.fromUri(this@CoreActivity, toSave)
                var destinationTree: UsefulDocumentFile? = null
                try {
                    destinationTree = getDocumentFile(destinationFolder, true, true)
                } catch (e: DocumentActivity.WritePermissionException) {
                    e.printStackTrace()
                }

                if (destinationTree == null) {
                    return@forEach
                }

                val desiredName = FileUtil.swapExtention(source.name, config.extension)
                val desiredUri = DocumentUtil.getChildUri(destinationFolder, desiredName)
                var destinationFile = UsefulDocumentFile.fromUri(this@CoreActivity, desiredUri)

                if (!destinationFile.exists())
                    destinationFile = destinationTree.createFile(null, desiredName)

                FileUtil.getParcelFileDescriptor(this@CoreActivity, source.uri, "r").use { inputPfd ->
                FileUtil.getParcelFileDescriptor(this@CoreActivity, destinationFile.uri, "w").use { outputPfd ->
                    if (outputPfd == null) return@forEach

                    when (config.type) {
                        ImageConfiguration.ImageType.jpeg -> {
                            val quality = (config as JpegConfiguration).quality
    //                                    if (wm != null) {
    //                                        ImageProcessor.writeThumb(inputPfd.fd, quality,
    //                                            outputPfd.fd, wm.watermark, wm.margins.array,
    //                                            wm.waterWidth, wm.waterHeight) && success
    //                                    } else {
                            ImageProcessor.writeThumb(inputPfd.fd, quality, outputPfd.fd)
    //                                    }
                        }
                        ImageConfiguration.ImageType.tiff -> {
                            val compress = (config as TiffConfiguration).compress
    //                                    if (wm != null) {
    //                                        success = ImageProcessor.writeTiff(desiredName, inputPfd.fd,
    //                                            outputPfd.fd, compress, wm.watermark, wm.margins.array,
    //                                            wm.waterWidth, wm.waterHeight) && success
    //                                    } else {
                            ImageProcessor.writeTiff(desiredName, inputPfd.fd, outputPfd.fd, compress)
    //                                    }
                        }
                        else -> throw UnsupportedOperationException("unimplemented save type.")
                    }

                    builder.setProgress(images.size, progress, false)
                    notificationManager.notify(0, builder.build())

                    onImageAdded(destinationFile.uri)
                    // TODO: FIXME
//                    dbInserts.add(MetaUtil.newInsert(this@CoreActivity, destinationFile.uri))
                    remainingImages.remove(toSave)
                }}
            }

            // When the loop is finished, updates the notification
            builder.setContentText("Complete")
                .setProgress(0,0,false) // Removes the progress bar
            notificationManager.notify(0, builder.build())
        }
            .subscribeOn(Schedulers.from(AppExecutors.DISK))
            .subscribeBy(
//                AlertDialog.Builder(this@CoreActivity)
//                    .setMessage("Add converted images to the library?")
//                    .setPositiveButton(R.string.positive) { _, _ -> MetaUtil.updateMetaDatabase(this@CoreActivity, dbInserts) }
//                    .setNegativeButton(R.string.negative) { _, _ -> /*dismiss*/ }.show()
                onComplete = {
                    clearWriteResume()
                    onImageSetChanged()
                },
                onError = {
                    builder.setContentText("Some images did not transfer")
                }
            )
    }

    @Throws(DocumentActivity.WritePermissionException::class)
    private fun deleteAssociatedFiles(image: MetadataTest): Boolean {
        val associatedFiles = ImageUtil.getAssociatedFiles(this, Uri.parse(image.uri))
        for (file in associatedFiles)
            deleteFile(file)
        return deleteFile(image.uri)
    }

    private fun deleteTask(images: Collection<MetadataTest>) {
        if (images.isEmpty()) return

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
        builder.setContentTitle(getString(R.string.importingImages))
            .setContentText("placeholder")
            .setSmallIcon(R.mipmap.ic_launcher)

        Completable.create {
            val remainingImages = ArrayList(images)

            val dbDeletes = ArrayList<ContentProviderOperation>()
            val progress = 0
            builder.setProgress(images.size, progress, false)
            notificationManager.notify(0, builder.build())

            images.forEach { toDelete ->
                setWriteResume(WriteActions.DELETE, arrayOf<Any>(remainingImages))
                try {
                    if (deleteAssociatedFiles(toDelete)) {
                        onImageRemoved(toDelete)
                        // TODO: FIXME
//                        dbDeletes.add(MetaUtil.newDelete(toDelete))
                    }
                } catch (e: DocumentActivity.WritePermissionException) {
                    e.printStackTrace()
                }

                remainingImages.remove(toDelete)
            }

            // When the loop is finished, updates the notification
            builder.setContentText("Complete")
                .setProgress(0,0,false) // Removes the progress bar
            notificationManager.notify(0, builder.build())

//            MetaUtil.updateMetaDatabase(this@CoreActivity, dbDeletes)
        }
            .subscribeOn(Schedulers.from(AppExecutors.DISK))
            .subscribeBy(
//                AlertDialog.Builder(this@CoreActivity)
//                    .setMessage("Add converted images to the library?")
//                    .setPositiveButton(R.string.positive) { _, _ -> MetaUtil.updateMetaDatabase(this@CoreActivity, dbInserts) }
//                    .setNegativeButton(R.string.negative) { _, _ -> /*dismiss*/ }.show()
                onComplete = {
                    clearWriteResume()
                    onImageSetChanged()
                },
                onError = {
                    builder.setContentText("Some images did not transfer")
                }
            )
    }

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
                onImageRemoved(toRecycle)
            }

            // TODO: FIXME
//            MetaUtil.updateMetaDatabase(this@CoreActivity, dbDeletes)
            return null
        }

        override fun onPostExecute(result: Void) {
//            if (!this@CoreActivity.isDestroyed && mProgressDialog != null)
//                mProgressDialog!!.dismiss()
            onImageSetChanged()
        }

        override fun onCancelled() {
            onImageSetChanged()
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
                    onImageAdded(toRestore)
                    // TODO: FIXME
//                    dbInserts.add(MetaUtil.newInsert(this@CoreActivity, toRestore))
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
            onImageSetChanged()
        }
    }

    protected inner class WriteXmpTask : AsyncTask<Any, Int, Boolean>() {
        override fun doInBackground(vararg params: Any): Boolean? {
            val xmpPairing = params[0] as MutableMap<Uri, ContentValues>
            val databaseUpdates = ArrayList<ContentProviderOperation>()

            val uris = xmpPairing.entries.iterator()
            while (uris.hasNext()) {
                setWriteResume(WriteActions.WRITE_XMP, arrayOf<Any>(xmpPairing))

                val pair = uris.next()// as Entry<*, *>
                if (pair.key == null)
                //AJM: Not sure how this can be null, #167
                    continue

                val values = pair.value
                // TODO: FIXME
//                databaseUpdates.add(MetaUtil.newUpdate(pair.key, values))

                //				final Metadata meta = new Metadata(); // TODO: need to pull the existing metadata, this wipes everything
                //				meta.addDirectory(new XmpDirectory());
                //				MetaUtil.updateSubject(meta, DbUtil.convertStringToArray(values.getAsString(Meta.SUBJECT)));
                //				MetaUtil.updateRating(meta, values.getAsInteger(Meta.RATING));
                //				MetaUtil.updateLabel(meta, values.getAsString(Meta.LABEL));

                //TODO: This logic needs cleanup
                val xmp = ImageUtil.getXmpFile(this@CoreActivity, pair.key) ?: continue

                val meta = MetaUtil.readXmp(this@CoreActivity, xmp)
                MetaUtil.updateXmpStringArray(meta, MetaUtil.SUBJECT, DbUtil.convertStringToArray(values.getAsString(Meta.SUBJECT)))
                MetaUtil.updateXmpInteger(meta, MetaUtil.RATING, values.getAsInteger(Meta.RATING))
                MetaUtil.updateXmpString(meta, MetaUtil.LABEL, values.getAsString(Meta.LABEL))
                //				MetaUtil.updateXmpString(meta, MetaUtil.CREATOR, "test");

                val xmpUri = xmp.uri
                val xmpDoc: UsefulDocumentFile?
                try {
                    xmpDoc = getDocumentFile(xmpUri, false, false) // For now use getDocumentFile to leverage write testing
                    //TODO: need DocumentActivity.openOutputStream
                } catch (e: DocumentActivity.WritePermissionException) {
                    // Write pending updates, method will resume with remaining images
                    // TODO: FIXME
//                    MetaUtil.updateMetaDatabase(this@CoreActivity, databaseUpdates)
                    return false
                }

                try {
                    contentResolver.openOutputStream(xmpDoc!!.uri)!!.use { os -> MetaUtil.writeXmp(os, meta) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                uris.remove()  //concurrency-safe remove
            }

            // TODO: FIXME
//            MetaUtil.updateMetaDatabase(this@CoreActivity, databaseUpdates)
            return true
        }

        override fun onPostExecute(result: Boolean?) {
            if (result!!)
                clearWriteResume()
        }
    }

    private enum class XmpUpdateField {
        Rating,
        Label,
        Subject
    }

    //TODO:
    private inner class PrepareXmpRunnable(private val update: XmpEditFragment.XmpEditValues, val updateType: XmpUpdateField) : Runnable {
//        private val selectedImages: Collection<Uri>?
//        private val projection = arrayOf(Meta.URI, Meta.RATING, Meta.LABEL, Meta.SUBJECT)

//        init {
//            this.selectedImages = selectedImages
//        }

        override fun run() {
            //			if (selectedImages.size() == 0)
            //				return;
            //
            //			// TODO: This can be done without cursor lookups, use position and lookup from cursor
            //			String[] selectionArgs = new String[selectedImages.size()];
            //			for (int i = 0; i < selectedImages.size(); i++)
            //			{
            //				selectionArgs[i] = selectedImages.get(i).toString();
            //			}
            //
            //
            //			Map<Uri, ContentValues> xmpPairs = new HashMap<>();
            //			// Grab existing metadata
            //			try(Cursor c = getContentResolver().query(Meta.CONTENT_URI,
            //					projection,
            //					DbUtil.createIN(Meta.URI, selectedImages.size()),
            //					selectionArgs,
            //					null))
            //			{
            //
            //				if (c == null)
            //					return;
            //
            //				// Create mappings with existing values
            //				while (c.moveToNext()) {
            //					ContentValues cv = new ContentValues(projection.length);
            //					DatabaseUtils.cursorRowToContentValues(c, cv);
            //					xmpPairs.put(Uri.parse(cv.getAsString(Meta.URI)), cv);
            //				}
            //			}
            //
            //			// Update singular fields in the existing values
            //			for (Map.Entry<Uri, ContentValues> xmpPair : xmpPairs.entrySet())
            //			{
            //				switch(updateType)
            //				{
            //					case label:
            //						xmpPair.getValue().put(Meta.LABEL, update.getLabel());
            //						break;
            //					case rating:
            //						xmpPair.getValue().put(Meta.RATING, update.getRating());
            //						break;
            //						// FIXME: This should prepare a subject junction update
            ////					case subject:
            ////						xmpPair.getValue().put(Meta.SUBJECT, DbUtil.convertArrayToString(update.subject));
            ////						break;
            //				}
            //			}
            //			writeXmp(xmpPairs);
        }
    }

    companion object {
//        private val TAG = CoreActivity::class.java.simpleName
        private const val NOTIFICATION_CHANNEL = "notifications"

        const val SWAP_BIN_DIR = "swap"
        const val RECYCLE_BIN_DIR = "recycle"

        private const val REQUEST_SAVE_AS_DIR = 15

        // Identifies a particular Loader being used in this component
//        const val META_LOADER_ID = 0

//        const val EXTRA_META_BUNDLE = "meta_bundle"
//        const val META_PROJECTION_KEY = "projection"
//        const val META_SELECTION_KEY = "selection"
//        const val META_SELECTION_ARGS_KEY = "selection_args"
//        const val META_SORT_ORDER_KEY = "sort_order"
//        const val META_DEFAULT_SORT = Meta.NAME + " ASC"

        private const val EXPIRATION = 5184000000L //~60 days
    }
}
