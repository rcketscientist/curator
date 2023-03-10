package com.anthonymandra.rawdroid

import android.app.Application
import android.os.StrictMode
import com.anthonymandra.rawdroid.data.AppDatabase
import com.anthonymandra.rawdroid.data.DataRepository
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.core.CrashlyticsCore
import com.crashlytics.android.ndk.CrashlyticsNdk
import io.fabric.sdk.android.Fabric

class App : Application() {
	val database: AppDatabase
		get() = AppDatabase.getInstance(this)

	val dataRepo: DataRepository
		get() = DataRepository.getInstance(database)

	override fun onCreate() {
		super.onCreate()

		val crashlyticsKit = Crashlytics.Builder()
			.core(CrashlyticsCore.Builder()
				.disabled(BuildConfig.DEBUG)
				.build())
			.build()

		Fabric.with(this, crashlyticsKit, CrashlyticsNdk())

		if (BuildConfig.DEBUG) {
			StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
				.detectDiskReads()
				.detectDiskWrites()
				.detectCustomSlowCalls()
//				.detectResourceMismatches()
//				.detectUnbufferedIo()
				.detectNetwork()
				.penaltyLog()
//				.penaltyDialog()
//				.penaltyDeath()
				.build())
			StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder()
//				.detectActivityLeaks()	FIXME: ViewerActivity leak
//				.detectCleartextNetwork()
//				.detectContentUriWithoutPermission()
				.detectFileUriExposure()
				.detectLeakedClosableObjects()
				.detectLeakedRegistrationObjects()
				.detectLeakedSqlLiteObjects()
//				.detectUntaggedSockets().
				.penaltyLog()
//				.penaltyDeath()
				.build())
		}
	}
}
