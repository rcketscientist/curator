package com.anthonymandra.framework;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ThreadedPriorityIntentService extends Service
{
	private static final int DEFAULT_PRIORITY = 0;
	public static final String EXTRA_PRIORITY = "priority";

	private final Executor mPool;
	private final Executor mPriorityPool;

	protected abstract int getNumNormalThreads();
	protected abstract int getNumPriorityThreads();

	private final ConcurrentHashMap<Integer, Boolean> mTasks = new ConcurrentHashMap<Integer, Boolean>();
	private final Handler mUiThreadHandler = new Handler(Looper.getMainLooper());
	private final AtomicInteger mCompletedJobs = new AtomicInteger();
	private final AtomicInteger mTotalJobs = new AtomicInteger();

	private final PriorityBlockingQueue<QueueItem> mQueue= new PriorityBlockingQueue<>();
	private boolean mRedelivery;

	private int mLatestStartId;

	protected ThreadedPriorityIntentService()
	{
		mPool = Executors.newFixedThreadPool(getNumNormalThreads());
		mPriorityPool = Executors.newFixedThreadPool(getNumPriorityThreads());
	}

    private final class QueueItem implements Comparable<QueueItem>
    {
        Intent intent;
        int priority;
        int startId;

        @Override
        public int compareTo(QueueItem another)
        {
            if (this.priority > another.priority)
            {
                return -1;
            }
            else if (this.priority < another.priority)
            {
                return 1;
            }
            else
            {
                return (this.startId < another.startId) ? -1 : 1;
            }
        }
    }

    private class Task implements Runnable
    {
        @Override
        public void run()
        {
            Integer startId = null;
            try
            {
                final QueueItem item = mQueue.take();
                startId = item.startId;
                onHandleIntent(item.intent);
                mCompletedJobs.incrementAndGet();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            finally
            {
                mTasks.put(startId, Boolean.FALSE);
                if (mQueue.isEmpty())
                {
                    mUiThreadHandler.post(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            checkStop();
                        }
                    });
                }
            }
        }
    }

    @Override
    public void onStart(Intent intent, int startId)
    {
        final QueueItem item = new QueueItem();
        item.intent = intent;
        item.startId = startId;

        // Define the priority and add to queue
        final int priority = getPriority(intent);
        item.priority = priority;
        mQueue.add(item);
        mTotalJobs.incrementAndGet();

        //  Multithread: Add to task list and fire on executor
        mLatestStartId = startId;
        mTasks.put(startId, Boolean.TRUE);
        mPool.execute(new Task());
    }

    protected final int getPriority(Intent intent)
    {
        return intent.getIntExtra(EXTRA_PRIORITY, DEFAULT_PRIORITY);
    }

    protected final boolean isHigherThanDefault(Intent intent)
    {
        return intent.getIntExtra(EXTRA_PRIORITY, DEFAULT_PRIORITY) > DEFAULT_PRIORITY;
    }

    /**
     * You should not override this method for your IntentService. Instead,
     * override {@link #onHandleIntent}, which the system calls when the IntentService
     * receives a start request.
     * @see android.app.Service#onStartCommand
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        onStart(intent, startId);
        return mRedelivery ? START_REDELIVER_INTENT : START_NOT_STICKY;
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

    // runs on UI thread, no need to worry about concurrent adding / removing of tasks
    private final void checkStop()
    {
        // must not stopSelf(startId) for the latest startId if there is an older task running.
        for (Iterator<Map.Entry<Integer, Boolean>> iterator = mTasks.entrySet().iterator(); iterator.hasNext(); )
        {
            Map.Entry<Integer, Boolean> entry = iterator.next();
            int startId = entry.getKey();
            boolean isRunning = entry.getValue();

            if (!isRunning && startId < mLatestStartId)
            {
                stopMe(startId);
                iterator.remove();
            }
        }

        // check if everything but the latest startId was stopped
        if (mTasks.size() == 1)
        {
            Boolean running = mTasks.get(mLatestStartId);
            if (running == Boolean.FALSE)
            {
                mTasks.remove(mLatestStartId);
                stopMe(mLatestStartId);
            }
        }
    }

    /**
     * Unless you provide binding for your service, you don't need to implement this
     * method, because the default implementation returns null.
     * @see android.app.Service#onBind
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    protected abstract void onHandleIntent(Intent intent);

    /**
     * Wrapper for {@link #stopSelf(int)} that ensures {@link #onStop()} fires
     * @param startId {@link #stopSelf(int)}
     */
    public final void stopMe(int startId)
    {
        onStop();
        stopSelf(startId);
    }

    /**
     * Override to do any cleanup immediately before the service fully stops
     */
    public void onStop() {}
}