package com.anthonymandra.framework;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;

/**
 * Expands on {@link android.app.IntentService} with a separate thread for {@link #EXTRA_HIGH_PRIORITY}
 * tasks.  Both "queues" remain single-threaded, if you were to use this without {@link #EXTRA_HIGH_PRIORITY}
 * it would behave exactly like {@link android.app.IntentService}
 */
public abstract class PriorityIntentService extends Service
{
	/**
	 * Set this extra to true to run in a separate high-priority queue.
	 */
	public static final String EXTRA_HIGH_PRIORITY = "EXTRA_HIGH_PRIORITY";
	private final String mName;

	private volatile Looper mServiceLooper;
	private volatile Looper mPriorityLooper;
	private volatile ServiceHandler mServiceHandler;
	private volatile ServiceHandler mPriorityHandler;

	private boolean mRedelivery;

    private final class ServiceHandler extends Handler {
        ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            onHandleIntent((Intent)msg.obj);
            stopSelf(msg.arg1);
        }
    }

	/**
	 * Creates an IntentService.  Invoked by your subclass's constructor.
	 *
	 * @param name Used to name the worker thread, important only for debugging.
	 */
	public PriorityIntentService(String name) {
		super();
		mName = name;
	}

	/**
	 * Sets intent redelivery preferences.  Usually called from the constructor
	 * with your preferred semantics.
	 *
	 * <p>If enabled is true,
	 * {@link #onStartCommand(Intent, int, int)} will return
	 * {@link Service#START_REDELIVER_INTENT}, so if this process dies before
	 * {@link #onHandleIntent(Intent)} returns, the process will be restarted
	 * and the intent redelivered.  If multiple Intents have been sent, only
	 * the most recent one is guaranteed to be redelivered.
	 *
	 * <p>If enabled is false (the default),
	 * {@link #onStartCommand(Intent, int, int)} will return
	 * {@link Service#START_NOT_STICKY}, and if the process dies, the Intent
	 * dies along with it.
	 */
	public void setIntentRedelivery(boolean enabled)
	{
		mRedelivery = enabled;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		HandlerThread serviceThread = new HandlerThread("IntentService[" + mName + "]");
		HandlerThread priorityThread = new HandlerThread("IntentService[" + mName + "-priority]");
		serviceThread.start();
		priorityThread.start();

		mServiceLooper = serviceThread.getLooper();
		mPriorityLooper = priorityThread.getLooper();

		mServiceHandler = new ServiceHandler(mServiceLooper);
		mPriorityHandler = new ServiceHandler(mPriorityLooper);
	}

    @Override
    public void onStart(Intent intent, int startId)
    {
	    ServiceHandler handler = isHighPriority(intent) ? mPriorityHandler : mServiceHandler;

	    Message msg = handler.obtainMessage();
	    msg.arg1 = startId;
	    msg.obj = intent;
	    handler.sendMessage(msg);
    }

    protected final boolean isHighPriority(Intent intent)
    {
        return intent.getBooleanExtra(EXTRA_HIGH_PRIORITY, false);
    }

    /**
     * You should not override this method for your IntentService. Instead,
     * override {@link #onHandleIntent}, which the system calls when the IntentService
     * receives a start request.
     * @see Service#onStartCommand
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        onStart(intent, startId);
        return mRedelivery ? START_REDELIVER_INTENT : START_NOT_STICKY;
    }

	@Override
	public void onDestroy() {
		mServiceLooper.quit();
		mPriorityLooper.quit();
	}

    /**
     * Unless you provide binding for your service, you don't need to implement this
     * method, because the default implementation returns null.
     * @see Service#onBind
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    protected abstract void onHandleIntent(Intent intent);
}