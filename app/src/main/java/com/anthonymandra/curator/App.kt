package com.anthonymandra.curator

import android.app.Application
import android.os.StrictMode
import com.anthonymandra.curator.data.AppDatabase
import com.anthonymandra.curator.data.DataRepository

class App : Application() {
	val database: AppDatabase
		get() = AppDatabase.getInstance(this)

	val dataRepo: DataRepository
		get() = DataRepository.getInstance(database)

	override fun onCreate() {
		super.onCreate()

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
