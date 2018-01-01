package com.anthonymandra.rawdroid;

import android.app.Application;
import android.os.StrictMode;

import com.anthonymandra.rawdroid.data.AppDatabase;
import com.anthonymandra.rawdroid.data.DataRepository;
import com.anthonymandra.util.AppExecutors;
import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;
import com.crashlytics.android.ndk.CrashlyticsNdk;
import com.squareup.leakcanary.LeakCanary;

import io.fabric.sdk.android.Fabric;

public class App extends Application
{
	private AppExecutors mAppExecutors;

	@Override
	public void onCreate()
	{
		super.onCreate();

		mAppExecutors = new AppExecutors();

		if (LeakCanary.isInAnalyzerProcess(this)) {
			// This process is dedicated to LeakCanary for heap analysis.
			// You should not init your app in this process.
			return;
		}
		LeakCanary.install(this);

		Crashlytics crashlyticsKit = new Crashlytics.Builder()
				.core(new CrashlyticsCore.Builder().disabled(com.anthonymandra.rawdroid.BuildConfig.DEBUG).build())
				.build();

		Fabric.with(this, crashlyticsKit, new CrashlyticsNdk());

		if (BuildConfig.DEBUG) {
			StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
					.detectDiskReads()
					.detectDiskWrites()
					.detectNetwork()   // or .detectAll() for all detectable problems
					.penaltyLog()
					.penaltyDeath()
					.build());
			StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
					.detectLeakedSqlLiteObjects()
//					.detectLeakedClosableObjects()  disabled to avoid disklrucache and focus on cursors
					.penaltyLog()
					.penaltyDeath()
					.build());
		}
	}

	public AppDatabase getDatabase() {
		return AppDatabase.getInstance(this);
	}
	public AppExecutors getAppExecutors() { return mAppExecutors; }

	public DataRepository getRepository() {
		return DataRepository.getInstance(getDatabase());
	}
}
