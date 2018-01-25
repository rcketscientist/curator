package com.anthonymandra.framework

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.*
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
import android.text.Html
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
import com.anthonymandra.imageprocessor.Watermark
import com.anthonymandra.rawdroid.*
import com.anthonymandra.rawdroid.BuildConfig
import com.anthonymandra.rawdroid.R
import com.anthonymandra.util.*
import com.anthonymandra.util.FileUtil
import com.crashlytics.android.Crashlytics
import com.inscription.ChangeLogDialog
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
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
    protected var mItemsForIntent: Collection<Uri> = Collections.emptyList()

    protected abstract val licenseHandler: LicenseHandler
    protected abstract val progressBar: ProgressBar
    protected abstract val selectedImages: Collection<Uri>

    protected val notificationManager: NotificationManager? =
        this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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

        if ("beta" == BuildConfig.FLAVOR_cycle && BuildConfig.BUILD_TIME + EXPIRATION < System.currentTimeMillis()) {
            Toast.makeText(this, "Beta has expired.", Toast.LENGTH_LONG).show()
            //TODO: Add link to Curator store page
            finish()
        }

        //TODO: Temporarily convert pref from string to int
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        try {
            // This will throw if it's not a string.
            val binSize = prefs.getString(FullSettingsActivity.KEY_RecycleBinSize, "n/a")

            prefs.edit().putInt(FullSettingsActivity.KEY_RecycleBinSize, Integer.parseInt(binSize)).commit()
        } catch (ignored: Exception) {
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

    private fun storeSelectionForIntent() {
        mItemsForIntent = selectedImages
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_SAVE_AS_DIR -> if (resultCode == RESULT_OK && data != null) {
                handleSaveDestinationResult(data.data)
            }
        }
    }

    private fun handleSaveDestinationResult(destination: Uri?) {
        //		storeSelectionForIntent();	// dialog resets CAB, so store first

        // TODO: Might want to figure out a way to get free space to introduce this check again
        //        long importSize = getSelectedImageSize();
        //        if (destination.getFreeSpace() < importSize)
        //        {
        //            Toast.makeText(this, R.string.warningNotEnoughSpace, Toast.LENGTH_LONG).show();
        //            return;
        //        }

        // Load default save config if it exists and automatically apply it
        val config = ImageConfiguration.loadPreference(this@CoreActivity)
        if (config != null) {
            saveImage(mItemsForIntent, destination, config)
            return
        }

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.save_dialog)
        dialog.setTitle(R.string.saveAs)

        val tabs = dialog.findViewById<View>(R.id.tabHost) as TabHost
        tabs.setup()

        val jpg = tabs.newTabSpec("JPG")
        val tif = tabs.newTabSpec("TIF")

        jpg.setContent(R.id.JPG)
        jpg.setIndicator("JPG")
        tabs.addTab(jpg)

        tif.setContent(R.id.TIFF)
        tif.setIndicator("TIFF")
        tabs.addTab(tif)

        val qualityText = dialog.findViewById<View>(R.id.valueQuality) as TextView
        val qualityBar = dialog.findViewById<View>(R.id.seekBarQuality) as SeekBar
        val compressSwitch = dialog.findViewById<View>(R.id.switchCompress) as Switch

        val setDefault = dialog.findViewById<View>(R.id.checkBoxSetDefault) as CheckBox
        setDefault.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                Snackbar.make(dialog.currentFocus!!,
                    Html.fromHtml(
                        resources.getString(R.string.saveDefaultConfirm) + "  "
                            + "<i>" + resources.getString(R.string.settingsReset) + "</i>"),
                    Snackbar.LENGTH_LONG)
                    .show()
            }
        }

        qualityBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                qualityText.text = progress.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        val save = dialog.findViewById<View>(R.id.buttonSave) as Button
        save.setOnClickListener {
            var config: ImageConfiguration = JpegConfiguration()
            when (tabs.currentTab) {
                0 //JPG
                -> {
                    config = JpegConfiguration()
                    config.quality = qualityBar.progress
                }
                1 //TIF
                -> {
                    config = TiffConfiguration()
                    config.compress = compressSwitch.isChecked
                }
            }
            dialog.dismiss()

            if (setDefault.isChecked)
                config.savePreference(this@CoreActivity)

            saveImage(mItemsForIntent, destination, config)
        }
        val cancel = dialog.findViewById<View>(R.id.buttonCancel) as Button
        cancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    override fun onResumeWriteAction(callingMethod: Enum<*>?, callingParameters: Array<Any>) {
        if (callingMethod == null) {
            Crashlytics.logException(Exception("Null Write Method"))
            return
        }

        if (callingMethod is WriteActions) {
            when (callingMethod) {
                CoreActivity.WriteActions.COPY -> CopyTask().execute(*callingParameters)
                CoreActivity.WriteActions.DELETE -> DeleteTask().execute(*callingParameters)
                CoreActivity.WriteActions.RECYCLE -> RecycleTask().execute(*callingParameters)
                CoreActivity.WriteActions.RENAME -> RenameTask().execute(*callingParameters)
                CoreActivity.WriteActions.RESTORE -> RestoreTask().execute(*callingParameters)
                CoreActivity.WriteActions.WRITE_XMP -> WriteXmpTask().execute(*callingParameters)
            }
        }
        clearWriteResume()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        xmpEditFragment = supportFragmentManager.findFragmentById(R.id.editFragment) as XmpEditFragment
        xmpEditFragment.setListener({ rating, label, subject ->
            val values = XmpEditFragment.XmpEditValues()
            values.Label = label
            values.Subject = subject
            values.Rating = rating
            writeXmpModifications(values)
        })
        xmpEditFragment.setLabelListener({ label ->
            val values = XmpEditFragment.XmpEditValues()
            values.Label = label

            Thread(PrepareXmpRunnable(values, XmpUpdateField.Label)).start()
        })
        xmpEditFragment.setRatingListener({ rating ->
            val values = XmpEditFragment.XmpEditValues()
            values.Rating = rating

            Thread(PrepareXmpRunnable(values, XmpUpdateField.Rating)).start()
        })
        xmpEditFragment.setSubjectListener({ subject ->
            val values = XmpEditFragment.XmpEditValues()
            values.Subject = subject

            Thread(PrepareXmpRunnable(values, XmpUpdateField.Subject)).start()
        })
        hideEditXmpFragment()
    }

    protected fun toggleEditXmpFragment() {
        if (xmpEditFragment.isHidden) {
            showEditXmpFragment()
        } else {
            hideEditXmpFragment()
        }
    }

    protected fun hideEditXmpFragment() {
        val ft = supportFragmentManager.beginTransaction()
        ft.hide(xmpEditFragment)
        ft.setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        ft.commit()
    }

    protected fun showEditXmpFragment() {
        val ft = supportFragmentManager.beginTransaction()
        ft.show(xmpEditFragment)
        ft.setCustomAnimations(android.R.anim.slide_out_right, android.R.anim.slide_in_left, android.R.anim.slide_out_right, android.R.anim.slide_in_left)
        ft.commit()
    }

    protected fun writeXmpModifications(values: XmpEditFragment.XmpEditValues) {
        val selection = selectedImages
        if (selection != null) {
            val cv = ContentValues()
            cv.put(Meta.LABEL, values.Label)
            cv.put(Meta.RATING, values.Rating)
            //FIXME: This should update the subject junction!
            //			cv.put(Meta.SUBJECT, DbUtil.convertArrayToString(values.Subject));

            val xmpPairing = HashMap<Uri, ContentValues>()
            for (uri in selection) {
                xmpPairing[uri] = cv
            }

            writeXmp(xmpPairing)
        }
    }

    protected fun writeXmp(xmpPairing: Map<Uri, ContentValues>) {
        WriteXmpTask().execute(xmpPairing)
    }

    /**
     * Create swap directory or clear the contents
     */
    protected fun createSwapDir() {
        mSwapDir = FileUtil.getDiskCacheDir(this, SWAP_BIN_DIR)
        if (!mSwapDir.exists()) {
            mSwapDir.mkdirs()
        }
    }

    protected fun clearSwapDir() {
        if (mSwapDir == null)
            return

        Thread(Runnable {
            val swapFiles = mSwapDir.listFiles()
            if (swapFiles != null) {
                for (toDelete in swapFiles) {
                    toDelete.delete()
                }
            }
        }).start()
    }

    protected fun createRecycleBin() {
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

    protected fun showRecycleBin() {
        val settings = PreferenceManager.getDefaultSharedPreferences(this)
        val useRecycle = settings.getBoolean(FullSettingsActivity.KEY_DeleteConfirmation, true)

        if (!useRecycle) return

        val keys = recycleBin.keys
        val filesToRestore = ArrayList<String>()
        val shortNames = ArrayList<String>(keys.size)
        for (key in keys) {
            shortNames.add(Uri.parse(key).lastPathSegment)
        }
        AlertDialog.Builder(this).setTitle(R.string.recycleBin)
            .setNegativeButton(R.string.emptyRecycleBin) { _, _ -> recycleBin.clearCache() }
            .setNeutralButton(R.string.neutral) { _, _ ->  } // cancel, do nothing
            .setPositiveButton(R.string.restoreFile) { _, _ ->
                if (!filesToRestore.isEmpty())
                    restoreFiles(filesToRestore)
            }
            .setMultiChoiceItems(shortNames.toTypedArray(), null, DialogInterface.OnMultiChoiceClickListener { dialog, which, isChecked ->
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
    protected fun deleteImage(toDelete: Uri) {
        val itemsToDelete = ArrayList<Uri>()
        itemsToDelete.add(toDelete)
        deleteImages(itemsToDelete)
    }

    protected fun deleteImages(itemsToDelete: Collection<Uri>) {
        if (itemsToDelete.size == 0) {
            Toast.makeText(baseContext, R.string.warningNoItemsSelected, Toast.LENGTH_SHORT).show()
            return
        }

        val settings = PreferenceManager.getDefaultSharedPreferences(this)
        val deleteConfirm = settings.getBoolean(FullSettingsActivity.KEY_DeleteConfirmation, true)
        val useRecycle = settings.getBoolean(FullSettingsActivity.KEY_UseRecycleBin, true)
        val justDelete: Boolean?
        val message: String
        var spaceRequired: Long = 0
        for (toDelete in itemsToDelete) {
            if (toDelete == null)
                continue

            val f = File(toDelete.path)
            if (f.exists())
                spaceRequired += f.length()
        }

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
        } else if (recycleBin == null) {
            justDelete = true
            message = getString(R.string.warningNoRecycleBin)
        } else {
            justDelete = false
            message = getString(R.string.warningDeleteExceedsRecycle) // This message applies to deletes exceeding bin size
        }

        if (justDelete || recycleBin.binSize < spaceRequired) {
            if (deleteConfirm) {
                AlertDialog.Builder(this).setTitle(R.string.prefTitleDeleteConfirmation).setMessage(message)
                    .setNeutralButton(R.string.neutral) { _, _ -> } // do nothing
                    .setPositiveButton(R.string.delete) { _, _ -> DeleteTask().execute(itemsToDelete) }
                    .show()
            } else {
                DeleteTask().execute(itemsToDelete)
            }
        } else {
            RecycleTask().execute(itemsToDelete)
        }
    }

    protected fun requestEmailIntent() {
        requestEmailIntent(null)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
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

    protected fun showRenameDialog(itemsToRename: Collection<Uri>) {
        @SuppressLint("InflateParams")
        val dialogView = LayoutInflater.from(this).inflate(R.layout.format_name, null)
        val format = dialogView.findViewById<View>(R.id.spinner1) as Spinner
        val nameText = dialogView.findViewById<View>(R.id.editTextFormat) as EditText
        val exampleText = dialogView.findViewById<View>(R.id.textViewExample) as TextView

        nameText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            @SuppressLint("SetTextI18n")
            override fun afterTextChanged(s: Editable) {
                exampleText.text = "Ex: " + formatRename(format.selectedItemPosition,
                    s.toString(),
                    itemsToRename.size - 1,
                    itemsToRename.size)!!
            }
        })

        format.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            @SuppressLint("SetTextI18n")
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                exampleText.text = "Ex: " + formatRename(format.selectedItemPosition,
                    nameText.text.toString(),
                    itemsToRename.size - 1,
                    itemsToRename.size)!!
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val renameDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.renameImages))
            .setView(dialogView)
            .setPositiveButton(R.string.rename) { dialog, which ->
                val customName = nameText.text.toString()
                val selected = format.selectedItemPosition
                RenameTask().execute(itemsToRename, selected, customName)
            }
            .setNegativeButton(R.string.neutral) { dialog, which -> }.create()

        //		final Spinner format = (Spinner) dialogView.findViewById(R.id.spinner1);
        //		final EditText nameText = (EditText) dialogView.findViewById(R.id.editTextFormat);

        renameDialog.setCanceledOnTouchOutside(true)
        renameDialog.show()
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

    protected fun requestShare() {
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
                if (convert)
                    share.add(SwapProvider.createSwapUri(this, selection))
                else
                    share.add(selection)
            }
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, share)
        } else {
            intent.action = Intent.ACTION_SEND
            if (convert)
                intent.putExtra(Intent.EXTRA_STREAM, SwapProvider.createSwapUri(this, selectedItems[0]))
            else
                intent.putExtra(Intent.EXTRA_STREAM, selectedItems[0])
        }

        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    class LicenseHandler(context: Context) : Handler() {
        private val mContext: WeakReference<Context>

        init {
            mContext = WeakReference(context)
        }

        override fun handleMessage(msg: Message) {
            val state = msg.data.getSerializable(License.KEY_LICENSE_RESPONSE) as License.LicenseState
            if (state == null || state.toString().startsWith("modified")) {
                for (i in 0..2)
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
     * @throws WritePermissionException
     */
    @Throws(IOException::class)
    private fun copyAssociatedFiles(fromImage: Uri, toImage: Uri): Boolean {
        if (ImageUtil.hasXmpFile(this, fromImage)) {
            copyFile(ImageUtil.getXmpFile(this, fromImage).uri,
                ImageUtil.getXmpFile(this, toImage).uri)
        }
        if (ImageUtil.hasJpgFile(this, fromImage)) {
            copyFile(ImageUtil.getJpgFile(this, fromImage).uri,
                ImageUtil.getJpgFile(this, toImage).uri)
        }
        return copyFile(fromImage, toImage)
    }

    fun copyImages(images: Collection<Uri>, destination: Uri) {
        setMaxProgress(images.size)
        updateMessage("Copying...")
        Observable.fromIterable(images)
            .flatMap({ image ->
                copyImage(image, destination)
                    .subscribeOn(Schedulers.io())
            }, 10)  // concurrency must be on inner
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<Uri> {
                override fun onSubscribe(d: Disposable) {}

                override fun onNext(uri: Uri) {
                    incrementProgress()
                }

                override fun onError(e: Throwable) {
                    //TODO
                }

                override fun onComplete() {
                    endProgress()
                    updateMessage(null)
                }
            })
    }

    private fun copyImage(uri: Uri, destinationFolder: Uri): Observable<Uri> {
        return Observable.fromCallable {
            val source = UsefulDocumentFile.fromUri(this@CoreActivity, uri)
            val destinationFile = DocumentUtil.getChildUri(destinationFolder, source.name)
            copyAssociatedFiles(uri, destinationFile)
            val cv = ContentValues()
            MetaUtil.getImageFileInfo(this, uri, cv)
            contentResolver.insert(Meta.CONTENT_URI, cv)
            onImageAdded(uri)
            destinationFile
        }
    }

    fun copyTask(images: Collection<Uri>, destinationFolder: Uri) {
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
        builder.setContentTitle(getString(R.string.importingImages))
            .setContentText("placeholder")
            .setSmallIcon(R.mipmap.ic_launcher)

        Completable.create {
            val remainingImages = ArrayList(images)

            val dbInserts = ArrayList<ContentProviderOperation>()
            val progress = 0
            builder.setProgress(images.size, progress, false)
            notificationManager?.notify(0, builder.build())
            for (toCopy in images) {
                try {
                    setWriteResume(WriteActions.COPY, arrayOf<Any>(remainingImages))

                    val source = UsefulDocumentFile.fromUri(this@CoreActivity, toCopy)
                    val destinationFile = DocumentUtil.getChildUri(destinationFolder, source.name)
                    copyAssociatedFiles(toCopy, destinationFile)
                    dbInserts.add(MetaUtil.newInsert(this@CoreActivity, destinationFile))
                } catch (e: DocumentActivity.WritePermissionException) {
                    e.printStackTrace()
                    return@create // exit condition: requesting write permission the restart
                } catch (e: IOException) {
                    e.printStackTrace()
                } catch (e: RuntimeException) {
                    e.printStackTrace()
                    Crashlytics.setString("uri", toCopy.toString())
                    Crashlytics.logException(e)
                }

                remainingImages.remove(toCopy)
                onImageAdded(toCopy)
                builder.setProgress(images.size, progress, false)
                notificationManager?.notify(0, builder.build())
            }
            // When the loop is finished, updates the notification
            builder.setContentText("Complete")
                .setProgress(0,0,false) // Removes the progress bar
            notificationManager?.notify(0, builder.build())

        }
            .subscribeOn(Schedulers.from(AppExecutors.DISK))
            .subscribeBy (
                onComplete = {
                    clearWriteResume()
                    onImageSetChanged()
                },
                onError = {
                    builder.setContentText("Some images did not transfer")
                }
            )
    }

    protected fun saveImage(images: Collection<Uri>?, destination: Uri?, config: ImageConfiguration) {
        if (images!!.size < 0)
            return

        // Just grab the first width and assume that will be sufficient for all images
        SaveImageTask().execute(images, destination,
            ImageUtil.getWatermark(this, images.iterator().next()), config)
    }

    inner class SaveImageTask : AsyncTask<Any, String, Boolean>(), OnCancelListener {
        internal val progressDialog = ProgressDialog(this@CoreActivity)
        internal val dbInserts = ArrayList<ContentProviderOperation>()

        override fun onPreExecute() {
            progressDialog.setTitle(getString(R.string.extractingImage))
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            progressDialog.setCanceledOnTouchOutside(true)
            progressDialog.setOnCancelListener(this)
            progressDialog.show()
        }

        override fun doInBackground(vararg params: Any): Boolean? {
            if (params[0] !is List<*> || (params[0] as List<*>)[0] !is Uri)
                throw IllegalArgumentException()

            val totalImages = params[0] as List<Uri>
            val remainingImages = ArrayList(totalImages)
            val destinationFolder = params[1] as Uri
            val wm = params[2] as Watermark
            val config = params[3] as ImageConfiguration

            progressDialog.max = totalImages.size

            var success = true
            for (toThumb in totalImages) {
                setWriteResume(WriteActions.SAVE_IMAGE, arrayOf(remainingImages, destinationFolder, wm, config))
                val source = UsefulDocumentFile.fromUri(this@CoreActivity, toThumb)
                var destinationTree: UsefulDocumentFile? = null
                try {
                    destinationTree = getDocumentFile(destinationFolder, true, true)
                } catch (e: DocumentActivity.WritePermissionException) {
                    e.printStackTrace()
                }

                if (destinationTree == null) {
                    success = false
                    continue
                }

                val desiredName = FileUtil.swapExtention(source.name, config.extension)
                publishProgress(desiredName)
                val desiredUri = DocumentUtil.getChildUri(destinationFolder, desiredName)
                var destinationFile = UsefulDocumentFile.fromUri(this@CoreActivity, desiredUri)

                if (!destinationFile.exists())
                    destinationFile = destinationTree.createFile(null, desiredName)

                try {
                    FileUtil.getParcelFileDescriptor(this@CoreActivity, source.uri, "r").use { inputPfd ->
                        FileUtil.getParcelFileDescriptor(this@CoreActivity, destinationFile.uri, "w").use { outputPfd ->
                            if (outputPfd == null) {
                                success = false
                                continue
                            }

                            when (config.type) {
                                ImageConfiguration.ImageType.jpeg -> {
                                    val quality = (config as JpegConfiguration).quality
                                    if (wm != null) {
                                        success = ImageProcessor.writeThumb(inputPfd.fd, quality,
                                            outputPfd.fd, wm.watermark, wm.margins.array,
                                            wm.waterWidth, wm.waterHeight) && success
                                    } else {
                                        success = ImageProcessor.writeThumb(inputPfd.fd, quality, outputPfd.fd) && success
                                    }
                                }
                                ImageConfiguration.ImageType.tiff -> {
                                    val compress = (config as TiffConfiguration).compress
                                    if (wm != null) {
                                        success = ImageProcessor.writeTiff(desiredName, inputPfd.fd,
                                            outputPfd.fd, compress, wm.watermark, wm.margins.array,
                                            wm.waterWidth, wm.waterHeight) && success
                                    } else {
                                        success = ImageProcessor.writeTiff(desiredName, inputPfd.fd,
                                            outputPfd.fd, compress)
                                    }
                                }
                                else -> throw UnsupportedOperationException("unimplemented save type.")
                            }

                            onImageAdded(destinationFile.uri)
                            dbInserts.add(MetaUtil.newInsert(this@CoreActivity, destinationFile.uri))
                            remainingImages.remove(toThumb)
                        }
                    }
                } catch (e: Exception) {
                    success = false
                }

                publishProgress()
            }

            return success //not used.
        }

        override fun onPostExecute(result: Boolean?) {
            AlertDialog.Builder(this@CoreActivity)
                .setMessage("Add converted images to the library?")
                .setPositiveButton(R.string.positive) { dialog, which -> MetaUtil.updateMetaDatabase(this@CoreActivity, dbInserts) }
                .setNegativeButton(R.string.negative) { dialog, which -> /*dismiss*/ }.show()

            onImageSetChanged()

            if (!this@CoreActivity.isDestroyed && progressDialog != null)
                progressDialog.dismiss()
        }

        override fun onProgressUpdate(vararg values: String) {
            if (values.size > 0) {
                progressDialog.setMessage(values[0])
            } else {
                progressDialog.incrementProgressBy(1)
            }
        }

        override fun onCancel(dialog: DialogInterface) {
            this.cancel(true)
        }
    }

    @Throws(DocumentActivity.WritePermissionException::class)
    private fun deleteAssociatedFiles(image: Uri): Boolean {
        val associatedFiles = ImageUtil.getAssociatedFiles(this, image)
        for (file in associatedFiles)
            deleteFile(file)
        return deleteFile(image)
    }

    protected inner class DeleteTask : AsyncTask<Any, Int, Boolean>(), OnCancelListener {
        override fun onPreExecute() {
//            mProgressDialog = ProgressDialog(this@CoreActivity)
//            mProgressDialog!!.setTitle(R.string.deletingFiles)
//            mProgressDialog!!.setOnCancelListener(this)
//            mProgressDialog!!.show()
        }

        override fun doInBackground(vararg params: Any): Boolean? {
            if (params[0] !is List<*> || (params[0] as List<*>)[0] !is Uri)
                throw IllegalArgumentException()

            // Create a copy to keep track of completed deletions in case this needs to be restarted
            // to request write permission
            val totalDeletes = params[0] as List<Uri>
            val remainingDeletes = ArrayList(totalDeletes)

//            mProgressDialog!!.max = totalDeletes.size

            val dbDeletes = ArrayList<ContentProviderOperation>()
            var totalSuccess = true
            for (toDelete in totalDeletes) {
                setWriteResume(WriteActions.DELETE, arrayOf<Any>(remainingDeletes))
                try {
                    if (deleteAssociatedFiles(toDelete)) {
                        onImageRemoved(toDelete)
                        dbDeletes.add(MetaUtil.newDelete(toDelete))
                    }
                } catch (e: DocumentActivity.WritePermissionException) {
                    e.printStackTrace()
                    // We'll be automatically requesting write permission so kill this process
                    totalSuccess = false
                }

                remainingDeletes.remove(toDelete)
            }

            MetaUtil.updateMetaDatabase(this@CoreActivity, dbDeletes)
            return totalSuccess
        }

        override fun onPostExecute(result: Boolean?) {
            if (result!!)
                clearWriteResume()
            else
                Snackbar.make(rootView, R.string.deleteFail, Snackbar.LENGTH_LONG).show()

//            if (!this@CoreActivity.isDestroyed && mProgressDialog != null)
//                mProgressDialog!!.dismiss()
            onImageSetChanged()
        }

        protected fun onProgressUpdate(vararg values: Int) {
//            mProgressDialog!!.progress = values[0]
            // setSupportProgress(values[0]);
        }

        override fun onCancelled() {
            onImageSetChanged()
        }

        override fun onCancel(dialog: DialogInterface) {
            this.cancel(true)
        }
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
                    dbDeletes.add(MetaUtil.newDelete(toRecycle))
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

            MetaUtil.updateMetaDatabase(this@CoreActivity, dbDeletes)
            return null
        }

        override fun onPostExecute(result: Void) {
//            if (!this@CoreActivity.isDestroyed && mProgressDialog != null)
//                mProgressDialog!!.dismiss()
            onImageSetChanged()
        }

        protected fun onProgressUpdate(vararg values: Int) {
//            mProgressDialog!!.progress = values[0]
            // setSupportProgress(values[0]);
        }

        override fun onCancelled() {
            onImageSetChanged()
        }

        override fun onCancel(dialog: DialogInterface) {
            this.cancel(true)
        }
    }

    protected fun restoreFiles(toRestore: List<String>) {
        RestoreTask().execute(toRestore)
    }

    protected inner class RestoreTask : AsyncTask<Any, Int, Boolean>() {
        override fun doInBackground(vararg params: Any): Boolean? {
            if (params.size == 0)
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
                    dbInserts.add(MetaUtil.newInsert(this@CoreActivity, toRestore))
                } catch (e: DocumentActivity.WritePermissionException) {
                    e.printStackTrace()
                    totalSuccess = false
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }

            MetaUtil.updateMetaDatabase(this@CoreActivity, dbInserts)
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

    @Throws(IOException::class)
    fun renameImage(source: Uri, baseName: String?, updates: ArrayList<ContentProviderOperation>): Boolean {
        val srcFile = UsefulDocumentFile.fromUri(this, source)
        val xmpFile = ImageUtil.getXmpFile(this, source)
        val jpgFile = ImageUtil.getJpgFile(this, source)

        val filename = srcFile.name
        val sourceExt = filename.substring(filename.lastIndexOf("."), filename.length)

        val srcRename = baseName!! + sourceExt
        val xmpRename = baseName + ".xmp"
        val jpgRename = baseName + ".jpg"

        // Do src first in case it's a jpg
        if (srcFile.renameTo(srcRename)) {
            var rename = DocumentUtil.getNeighborUri(source, srcRename)
            if (rename == null) {
                val parent = srcFile.parentFile ?: return false
                val file = parent.findFile(srcRename) ?: return false
                rename = file.uri
            }
            val imageValues = ContentValues()
            imageValues.put(Meta.NAME, srcRename)
            imageValues.put(Meta.URI, rename!!.toString())

            updates.add(
                ContentProviderOperation.newUpdate(Meta.CONTENT_URI)
                    .withSelection(Meta.URI_SELECTION, arrayOf(source.toString()))
                    .withValues(imageValues)
                    .build())
        } else
            return false

        xmpFile.renameTo(xmpRename)

        if (jpgFile.renameTo(jpgRename)) {
            var rename = DocumentUtil.getNeighborUri(source, srcRename)
            if (rename == null) {
                val parent = srcFile.parentFile ?: return false
                val file = parent.findFile(jpgRename) ?: return false
                rename = file.uri
            }

            val jpgValues = ContentValues()
            jpgValues.put(Meta.NAME, jpgRename)
            jpgValues.put(Meta.URI, rename!!.toString())

            updates.add(
                ContentProviderOperation.newUpdate(Meta.CONTENT_URI)
                    .withSelection(Meta.URI_SELECTION, arrayOf(jpgFile.uri.toString()))
                    .withValues(jpgValues)
                    .build())
        }

        return true
    }

    protected inner class RenameTask : AsyncTask<Any, Int, Boolean>() {
        override fun doInBackground(vararg params: Any): Boolean? {
            if (params[0] !is List<*> || (params[0] as List<*>)[0] !is Uri)
                throw IllegalArgumentException()

            val totalImages = params[0] as List<Uri>
            val remainingImages = ArrayList(totalImages)

            val format = params[1] as Int
            val customName = params[2] as String

            var counter = 0
            val total = totalImages.size
            val operations = ArrayList<ContentProviderOperation>()

            try {
                for (image in totalImages) {
                    ++counter
                    setWriteResume(WriteActions.RENAME, arrayOf<Any>(remainingImages, format, customName))

                    val rename = formatRename(format, customName, counter, total)

                    renameImage(image, rename, operations)

                    if (renameImage(image, rename, operations)) {
                        remainingImages.remove(image)
                    }
                }
            } catch (e: DocumentActivity.WritePermissionException) {
                e.printStackTrace()
                return false
            } catch (e: IOException) {
                e.printStackTrace()
            }

            MetaUtil.updateMetaDatabase(this@CoreActivity, operations)
            return true
        }

        override fun onPostExecute(result: Boolean?) {
            if (result!!)
                clearWriteResume()
        }
    }

    protected inner class WriteXmpTask : AsyncTask<Any, Int, Boolean>() {
        override fun doInBackground(vararg params: Any): Boolean? {
            val xmpPairing = params[0] as Map<Uri, ContentValues>
            val databaseUpdates = ArrayList<ContentProviderOperation>()

            val uris = xmpPairing.entries.iterator()
            while (uris.hasNext()) {
                setWriteResume(WriteActions.WRITE_XMP, arrayOf<Any>(xmpPairing))

                val pair = uris.next()// as Entry<*, *>
                if (pair.key == null)
                //AJM: Not sure how this can be null, #167
                    continue

                val values = pair.value
                databaseUpdates.add(MetaUtil.newUpdate(pair.key, values))

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
                    MetaUtil.updateMetaDatabase(this@CoreActivity, databaseUpdates)
                    return false
                }

                try {
                    contentResolver.openOutputStream(xmpDoc!!.uri)!!.use { os -> MetaUtil.writeXmp(os, meta) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                uris.remove()  //concurrency-safe remove
            }

            MetaUtil.updateMetaDatabase(this@CoreActivity, databaseUpdates)
            return true
        }

        override fun onPostExecute(result: Boolean?) {
            if (result!!)
                clearWriteResume()
        }
    }

    internal enum class XmpUpdateField {
        Rating,
        Label,
        Subject
    }

    protected inner class PrepareXmpRunnable(private val update: XmpEditFragment.XmpEditValues, val updateType: XmpUpdateField) : Runnable {
//        private val selectedImages: Collection<Uri>?
        private val projection = arrayOf(Meta.URI, Meta.RATING, Meta.LABEL, Meta.SUBJECT)

        init {
//            this.selectedImages = selectedImages
        }

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
            //					case Label:
            //						xmpPair.getValue().put(Meta.LABEL, update.getLabel());
            //						break;
            //					case Rating:
            //						xmpPair.getValue().put(Meta.RATING, update.getRating());
            //						break;
            //						// FIXME: This should prepare a subject junction update
            ////					case Subject:
            ////						xmpPair.getValue().put(Meta.SUBJECT, DbUtil.convertArrayToString(update.Subject));
            ////						break;
            //				}
            //			}
            //			writeXmp(xmpPairs);
        }
    }

    companion object {

        private val TAG = CoreActivity::class.java.simpleName
        private const val NOTIFICATION_CHANNEL = "notifications"

        val SWAP_BIN_DIR = "swap"
        val RECYCLE_BIN_DIR = "recycle"

        private val REQUEST_SAVE_AS_DIR = 15

        // Identifies a particular Loader being used in this component
        val META_LOADER_ID = 0

        val EXTRA_META_BUNDLE = "meta_bundle"
        val META_PROJECTION_KEY = "projection"
        val META_SELECTION_KEY = "selection"
        val META_SELECTION_ARGS_KEY = "selection_args"
        val META_SORT_ORDER_KEY = "sort_order"
        val META_DEFAULT_SORT = Meta.NAME + " ASC"

        private val EXPIRATION = 5184000000L //~60 days

        private fun numDigits(x: Int): Int {
            return if (x < 10)
                1
            else
                if (x < 100)
                    2
                else
                    if (x < 1000)
                        3
                    else
                        if (x < 10000)
                            4
                        else
                            if (x < 100000)
                                5
                            else
                                if (x < 1000000)
                                    6
                                else
                                    if (x < 10000000)
                                        7
                                    else
                                        if (x < 100000000)
                                            8
                                        else
                                            if (x < 1000000000) 9 else 10
        }

        private fun formatRename(format: Int, baseName: String, index: Int, total: Int): String? {
            val sequencer = "%0" + numDigits(total) + "d"

            var rename: String? = null
            when (format) {
                0 -> rename = baseName + "-" + String.format(sequencer, index)
                1 -> rename = baseName + " (" + String.format(sequencer, index) + " of " + total + ")"
            }

            return rename
        }
    }
}
