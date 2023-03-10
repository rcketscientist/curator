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
import com.anthonymandra.rawdroid.data.ImageInfo
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
		// TODO: We should order these by capture time
		private val itemsToRename: Collection<ImageInfo>) : Dialog(activity) {

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
			dismiss()
		}

		cancelButton.setOnClickListener { dismiss() }
	}

	fun renameImages(images: Collection<ImageInfo>, format: Int, customName: String) {
		var fileCounter = 0
		var progressCounter = 0
		val builder = NotificationCompat.Builder(context, CoreActivity.NOTIFICATION_CHANNEL)

		Observable.fromIterable(images)
				.subscribeOn(Schedulers.from(AppExecutors.DISK))
				.map {
					++fileCounter
					val rename = formatRename(format, customName, fileCounter, images.size)
					renameImage(it, rename)
					it
				}.observeOn(Schedulers.from(AppExecutors.MAIN))
				.doOnSubscribe {
					activity.setMaxProgress(images.size)
					builder.setContentTitle(context.getString(R.string.renameImages))
							.setSmallIcon(R.drawable.ic_notification)
							.setProgress(images.size, 0, false)
					activity.notificationManager.notify(0, builder.build())
				}
				.subscribeBy(
						onNext = {
							activity.incrementProgress()
							builder.setProgress(images.size, ++progressCounter, false)
							builder.setContentText(it.name)
							activity.notificationManager.notify(0, builder.build())
						},
						onComplete = {
							activity.endProgress()
							builder.setContentText("Complete").setProgress(0, 0, false)
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

	private fun renameImage(image: ImageInfo, baseName: String) {
		val dataRepo = DataRepository.getInstance(context)

		val source = Uri.parse(image.uri)
		val srcFile = UsefulDocumentFile.fromUri(context, source)
		val xmpFile = ImageUtil.getXmpFile(context, source)
		val jpgFile = ImageUtil.getJpgFile(context, source)

		val filename = srcFile.name ?: return
		val sourceExt = filename.substring(filename.lastIndexOf("."), filename.length)

		val srcRename = baseName + sourceExt
		val xmpRename = "$baseName.xmp"
		val jpgRename = "$baseName.jpg"

		if (srcFile.name == srcRename) {
			return // Don't rename twice.
		}

		// Do src first in case it's a jpg
		if (srcFile.renameTo(srcRename)) {
			image.name = filename
			image.uri = srcFile.uri.toString()
			image.documentId = srcFile.documentId ?: return
			dataRepo.updateMeta(image).subscribe()
		}

		xmpFile.renameTo(xmpRename)

		if (jpgFile.renameTo(jpgRename)) {
			val originalJpg = dataRepo.synchImage(jpgFile.uri.toString())
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
		val sequencer = "%0${numDigits(total)}d"
		val number = String.format(sequencer, index)
		return when (format) {
			0 -> "$baseName-$number"
			1 -> "$baseName ($number of $total)"
			else -> throw UnknownFormatConversionException("Format $format is unknown.")
		}
	}
}