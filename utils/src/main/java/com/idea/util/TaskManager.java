package com.idea.util;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.di.DependencyInjector;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author by Null-Pointer on 9/27/2015.
 */
public class TaskManager {

    public static final String TAG = TaskManager.class.getSimpleName();
    private static final AtomicBoolean initialised = new AtomicBoolean(false);

    private static ThreadFactory factory = new ThreadFactory() {
        @Override
        public Thread newThread(@NonNull Runnable r) {
            return new SmartThread(r);
        }
    };
    private static ExecutorService cachedThreadPool = Executors.newCachedThreadPool(factory);
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = CPU_COUNT + 3;
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 4;
    private static final int KEEP_ALIVE = 4;

    private static final BlockingQueue<Runnable> nonNetworkWorkQueue =
            new LinkedBlockingQueue<>(128), networkWorkQueue = new LinkedBlockingQueue<>(256);

    public static final ExecutorService NON_NETWORK_EXECUTOR
            = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE,
            TimeUnit.SECONDS, nonNetworkWorkQueue),
            NETWORK_EXECUTOR
                    = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE,
                    TimeUnit.SECONDS, networkWorkQueue);

    private static JobManager jobManager;


    public static void init(Application application, DependencyInjector injector) {
        if (!initialised.getAndSet(true)) {
            Configuration config = new Configuration.Builder(application)
                    .injector(injector)
                    .customLogger(new PLog("JobManager"))
                    .jobSerializer(new Task.JobSerializer())
                    .build();
            jobManager = new JobManager(application, config);
            jobManager.start();
            PLog.w(TAG, "initialising %s", TAG);
            final Timer timer = new Timer("cleanup Timer", true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        File file = Config.getTempDir();
                        if (file.exists()) {
                            org.apache.commons.io.FileUtils.cleanDirectory(Config.getTempDir());
                        }
                    } catch (IOException ignored) {

                    } finally {
                        timer.cancel();
                    }
                }
            }, 1); //immediately

        }
    }

    @SuppressWarnings("unused")
    public static long runJob(Task job) {
        if (job == null || !job.isValid()) {
            throw new IllegalArgumentException("invalid job");
        }
        return jobManager.addJob(job);
    }

    public static void executeOnMainThread(Runnable r) {
        ensureInitialised();
        new Handler(Looper.getMainLooper()).post(r);
    }


    public static Future<?> execute(Runnable task, boolean requiresNetwork) {
        ensureInitialised();
        if (requiresNetwork) {
            return NETWORK_EXECUTOR.submit(task);
        } else {
            return NON_NETWORK_EXECUTOR.submit(task);
        }
    }

    private static int expressQueueLength = 0;
    private static final int maxLength = Runtime.getRuntime().availableProcessors() * 15;


    public static Future<?> executeNow(Runnable runnable, boolean requiresNetwork) {
        ensureInitialised();
        synchronized (expressQueueLock) {
            if (expressExecutionQueueTooLong()) {
                return execute(runnable, requiresNetwork);
            }
            expressQueueLength++;
        }
        return cachedThreadPool.submit(runnable);
    }

    private static boolean expressExecutionQueueTooLong() {
        return expressQueueLength >= maxLength;
    }

    private static void ensureInitialised() {
        if (!initialised.get()) {
            throw new IllegalArgumentException("did you forget to init()?");
        }
    }

    private static class SmartThread extends Thread {
        private final Runnable target;

        private SmartThread(Runnable r) {
            target = r;
        }

        @Override
        public void run() {
            target.run();
            synchronized (expressQueueLock) {
                expressQueueLength--;
            }
        }
    }

    private static final Object expressQueueLock = new Object();

}
