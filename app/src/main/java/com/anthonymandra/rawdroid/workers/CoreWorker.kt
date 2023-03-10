package com.anthonymandra.rawdroid.workers

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.anthonymandra.rawdroid.R
import com.anthonymandra.util.Util

abstract class CoreWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
	abstract val channelId: String
	abstract val channelName: String
	abstract val channelDescription: String
	abstract val notificationTitle: String
	private val notifications = NotificationManagerCompat.from(applicationContext)
	private val builder by lazy {
		Util.createNotificationChannel(
			applicationContext,
			channelId,
			channelName,
			channelDescription)

		Util.createNotification(
			applicationContext,
			channelId,
			notificationTitle,
			"")
	}

	private fun NotificationManagerCompat.notify(notification: Notification) {
		this.notify(tags.first(), id.hashCode(), notification)
	}

	protected fun sendPeekNotification(
		contentText: String = applicationContext.getString(R.string.preparing)) {
		builder
			.setOnlyAlertOnce(true)
			.setContentText(contentText)
		notifications.notify(builder.build())
	}

	protected fun sendUpdateNotification(contentText: String,
													 progress: Int = 0,
													 workTotal: Int = 0) {
		builder
			.setProgress(workTotal, progress, false)
			.setContentText(contentText)
		notifications.notify(builder.build())
	}

	protected fun sendCompletedNotification() {
		builder
			.setContentText("Complete")
			.setProgress(0, 0, false)
			.setOnlyAlertOnce(false)
		notifications.notify(builder.build())
	}

	protected fun sendCancelledNotification() {
		builder
			.setContentText("Cancelled")
			.setOnlyAlertOnce(false)
		notifications.notify(builder.build())
	}
}