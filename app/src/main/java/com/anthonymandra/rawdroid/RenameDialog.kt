package com.anthonymandra.rawdroid

import android.annotation.SuppressLint
import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import androidx.core.app.NotificationCompat
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import com.anthonymandra.framework.CoreActivity
import com.anthonymandra.framework.UsefulDocumentFile
import com.anthonymandra.rawdroid.data.DataRepository
import com.anthonymandra.rawdroid.data.MetadataTest
import com.anthonymandra.util.AppExecutors
import com.anthonymandra.util.ImageUtil
import com.crashlytics.android.Crashlytics
import io.reactivex.Observable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.format_name.*
import java.util.*

class RenameDialog(
    private val activity: CoreActivity,
    private val itemsToRename: Collection<MetadataTest>) : Dialog(activity) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.format_name)
        setTitle(context.getString(R.string.renameImages))
        setCanceledOnTouchOutside(true)

        nameTextView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            @SuppressLint("SetTextI18n")
            override fun afterTextChanged(s: Editable) {
                updateExample()
            }
        })

        formatSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            @SuppressLint("SetTextI18n")
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                updateExample()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        renameButton.setOnClickListener {
            val customName = nameTextView.text.toString()
            val selected = formatSpinner.selectedItemPosition
            renameImages(itemsToRename, selected, customName)
        }

        cancelButton.setOnClickListener { dismiss() }
    }

    fun renameImages(images: Collection<MetadataTest>, format: Int, customName: String) {
        var counter = 0
        val builder = NotificationCompat.Builder(context, CoreActivity.NOTIFICATION_CHANNEL)

        Observable.fromIterable(images)
            .subscribeOn(Schedulers.from(AppExecutors.DISK))
            .map {
                ++counter
                val rename = formatRename(format, customName, counter, images.size)
                renameImage(it, rename)
                it
            }.observeOn(Schedulers.from(AppExecutors.MAIN))
            .doOnSubscribe {
                counter = 0
                activity.setMaxProgress(images.size)
                builder.setContentTitle(context.getString(R.string.renameImages))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setProgress(images.size, 0, false)
                activity.notificationManager.notify(0, builder.build())
            }
            .subscribeBy (
                onNext = {
                    activity.incrementProgress()
                    builder.setProgress(images.size, ++counter, false)
                    builder.setContentText(it.name)
                    activity.notificationManager.notify(0, builder.build())
                },
                onComplete = {
                    activity.endProgress()
                    builder.setContentText("Complete").setProgress(0,0,false)
                    activity.notificationManager.notify(0, builder.build())
                },
                onError = {
                    activity.incrementProgress()
                    activity.notificationManager.notify(0, builder.build())
                    it.printStackTrace()
                    Crashlytics.logException(it)
                }
            )
    }

    private fun renameImage(image: MetadataTest, baseName: String) {
        val dataRepo = DataRepository.getInstance(context)

        val source = Uri.parse(image.uri)
        val srcFile = UsefulDocumentFile.fromUri(context, source)
        val xmpFile = ImageUtil.getXmpFile(context, source)
        val jpgFile = ImageUtil.getJpgFile(context, source)

        val filename = srcFile.name
        val sourceExt = filename.substring(filename.lastIndexOf("."), filename.length)

        val srcRename = baseName + sourceExt
        val xmpRename = "$baseName.xmp"
        val jpgRename = "$baseName.jpg"

        // Do src first in case it's a jpg
        if (srcFile.renameTo(srcRename)) {
            // TODO: This seems senseless, musta been a bug fix?
//            var rename = DocumentUtil.getNeighborUri(source, srcRename)
//            if (rename == null) {
//                val parent = srcFile.parentFile ?: return
//                val file = parent.findFile(srcRename) ?: return
//                rename = file.uri
//            }

            image.name = srcFile.name
            image.uri = srcFile.uri.toString()
            dataRepo.updateMeta(image).subscribe()
        }

        xmpFile.renameTo(xmpRename)

        if (jpgFile.renameTo(jpgRename)) {
            // TODO: This seems senseless, musta been a bug fix?
//            var rename = DocumentUtil.getNeighborUri(source, srcRename)
//            if (rename == null) {
//                val parent = srcFile.parentFile ?: return
//                val file = parent.findFile(jpgRename) ?: return
//                rename = file.uri
//            }

            val originalJpg = dataRepo.imageBlocking(jpgFile.uri.toString())
            originalJpg.name = jpgFile.name
            originalJpg.uri = jpgFile.uri.toString()
            dataRepo.updateMeta(originalJpg).subscribe()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateExample() {
        exampleTextView.text = "Ex: " + formatRename(formatSpinner.selectedItemPosition,
                nameTextView.text.toString(),
                itemsToRename.size - 1,
                itemsToRename.size)
    }

    private fun numDigits(x: Int): Int {
        return when {
            x < 10 -> 1
            x < 100 -> 2
            x < 1000 -> 3
            x < 10000 -> 4
            x < 100000 -> 5
            x < 1000000 -> 6
            x < 10000000 -> 7
            x < 100000000 -> 8
            x < 1000000000 -> 9
            else -> 10
        }
    }

    private fun formatRename(format: Int, baseName: String, index: Int, total: Int): String {
        val sequencer = "%0" + numDigits(total) + "d"
        return when (format) {
            0 -> baseName + "-" + String.format(sequencer, index)
            1 ->  baseName + " (" + String.format(sequencer, index) + " of " + total + ")"
            else -> throw UnknownFormatConversionException("Format $format is unknown.")
        }
    }
}