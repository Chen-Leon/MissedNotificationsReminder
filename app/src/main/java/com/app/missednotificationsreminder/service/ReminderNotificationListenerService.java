package com.app.missednotificationsreminder.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Display;

import com.app.missednotificationsreminder.di.Injector;
import com.app.missednotificationsreminder.di.qualifiers.ReminderEnabled;
import com.app.missednotificationsreminder.di.qualifiers.ReminderInterval;
import com.app.missednotificationsreminder.di.qualifiers.SchedulerEnabled;
import com.app.missednotificationsreminder.di.qualifiers.SchedulerMode;
import com.app.missednotificationsreminder.di.qualifiers.SchedulerRangeBegin;
import com.app.missednotificationsreminder.di.qualifiers.SchedulerRangeEnd;
import com.app.missednotificationsreminder.di.qualifiers.SelectedApplications;
import com.app.missednotificationsreminder.util.TimeUtils;
import com.f2prateek.rx.preferences.Preference;

import java.util.Date;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import dagger.ObjectGraph;
import rx.Observable;
import rx.exceptions.OnErrorThrowable;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;
import timber.log.Timber;

/**
 * The service to monitor all status bar notifications. It performs periodical sound notification depend on whether
 * there are available notifications from applications which matches user selected applications. The notification interval
 * is also specified by the user in the corresponding window.
 *
 * @author Eugene Popovich
 */
public class ReminderNotificationListenerService extends AbstractReminderNotificationListenerService {
    /**
     * Action for the pending intent used by alarm manager to periodically wake the device and send broadcast with this
     * action
     */
    static final String PENDING_INTENT_ACTION = ReminderNotificationListenerService.class.getCanonicalName();
    /**
     * The constant used to identify handler message to start checking of the service waking conditions
     */
    static final int CHECK_WAKING_CONDITIONS_MSG = 0;

    @Inject @ReminderEnabled Preference<Boolean> reminderEnabled;
    @Inject @ReminderInterval Preference<Integer> reminderInterval;
    @Inject @SelectedApplications Preference<Set<String>> selectedApplications;
    @Inject @SchedulerEnabled Preference<Boolean> schedulerEnabled;
    @Inject @SchedulerMode Preference<Boolean> schedulerMode;
    @Inject @SchedulerRangeBegin Preference<Integer> schedulerRangeBegin;
    @Inject @SchedulerRangeEnd Preference<Integer> schedulerRangeEnd;

    /**
     * Alarm manager to schedule/cancel periodical actions
     */
    AlarmManager mAlarmManager;
    /**
     * The pending intent used by alarm manager to wake the service
     */
    PendingIntent mPendingIntent;

    /**
     * The flag to indicate periodical notification active state
     */
    private AtomicBoolean mActive = new AtomicBoolean();
    /**
     * Composite subscription used to handle subscriptions added in this service
     */
    private CompositeSubscription mSubscriptions = new CompositeSubscription();
    /**
     * Receiver used to handle actions from the pending intent used for periodical alarms
     */
    private ScheduledSoundNotificationReceiver mPendingIntentReceiver;
    /**
     * The handler used to process various service related messages
     */
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CHECK_WAKING_CONDITIONS_MSG:
                    Timber.d("CHECK_WAKING_CONDITIONS_MSG message received");
                    checkWakingConditions();
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Timber.d("onCreate");

        // inject dependencies
        ObjectGraph appGraph = Injector.obtain(getApplication());
        appGraph.inject(this);

        // initialize broadcast receiver
        mPendingIntentReceiver = new ScheduledSoundNotificationReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(PENDING_INTENT_ACTION);
        registerReceiver(mPendingIntentReceiver, filter);

        // initialize alarm manager and pending intent
        mAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent i = new Intent(PENDING_INTENT_ACTION);
        mPendingIntent = PendingIntent.getBroadcast(this, 0, i, PendingIntent.FLAG_CANCEL_CURRENT);

        // initialize preferences changes listeners
        mSubscriptions.add(
                reminderEnabled.asObservable()
                        .skip(1) // skip initial value emitted right after the subscription
                        .filter(enabled -> enabled) // if reminder enabled
                        .subscribe(b -> sendCheckWakingConditionsCommand()));
        mSubscriptions.add(
                reminderEnabled.asObservable()
                        .skip(1) // skip initial value emitted right after the subscription
                        .filter(enabled -> !enabled) // if reminder disabled
                        .subscribe(b -> stopWaking()));
        mSubscriptions.add(
                Observable.merge(
                        reminderInterval.asObservable()
                                .skip(1) // skip initial value emitted right after the subscription
                                .doOnEach(__ -> Timber.d("Reminder interval changed"))
                                .map(__ -> true),
                        selectedApplications.asObservable()
                                .skip(1) // skip initial value emitted right after the subscription
                                .doOnEach(__ -> Timber.d("Selected applications changed"))
                                .map(__ -> true),
                        schedulerEnabled.asObservable()
                                .skip(1) // skip initial value emitted right after the subscription
                                .doOnEach(__ -> Timber.d("Scheduler enabled changed"))
                                .map(__ -> true),
                        schedulerMode.asObservable()
                                .skip(1) // skip initial value emitted right after the subscription
                                .doOnEach(__ -> Timber.d("Scheduler mode changed"))
                                .map(__ -> true),
                        schedulerRangeBegin.asObservable()
                                .skip(1) // skip initial value emitted right after the subscription
                                .doOnEach(__ -> Timber.d("Scheduler range begin changed"))
                                .map(__ -> true),
                        schedulerRangeEnd.asObservable()
                                .skip(1) // skip initial value emitted right after the subscription
                                .doOnEach(__ -> Timber.d("Scheduler range end changed"))
                                .map(__ -> true))
                        .subscribe(data -> {
                            // restart alarm with new conditions if necessary
                            stopWaking();
                            sendCheckWakingConditionsCommand();
                        }));
        sendCheckWakingConditionsCommand();
    }

    /**
     * Send the check waing condition message to the service handler
     */
    private void sendCheckWakingConditionsCommand() {
        Timber.d("sendCheckWakingConditionsCommand");
        mHandler.sendMessage(mHandler.obtainMessage(CHECK_WAKING_CONDITIONS_MSG));
    }

    /**
     * Check whether the waking alarm should be scheduled or no
     */
    private void checkWakingConditions() {
        try {
            Timber.d("checkWakingConditions");
            if (mActive.get()) {
                Timber.d("checkWakingConditions: already active, skipping");
                return;
            }
            if (!reminderEnabled.get()) {
                Timber.d("checkWakingConditions: disabled, skipping");
                return;
            }
            boolean schedule = checkNotificationForAtLeastOnePackageExists(selectedApplications.get());

            if (schedule) {
                Timber.d("checkWakingConditions: there are notifications from selected applications. Scheduling reminder");
                // remember active state
                mActive.set(true);
                scheduleNextWakup();
            } else {
                Timber.d("checkWakingConditions: there are no notifications from selected applications to periodically remind");
            }
        } catch (Throwable t) {
            Timber.e(t, "Unexpected failure");
        }
    }

    /**
     * Schedule wakup alarm for the sound notification pending intent
     */
    private void scheduleNextWakup() {
        long scheduledTime = 0;
        if (schedulerEnabled.get()) {
            // if custom scheduler is enabled
            scheduledTime = TimeUtils.getScheduledTime(
                    schedulerMode.get()? TimeUtils.SchedulerMode.WORKING_PERIOD: TimeUtils.SchedulerMode.NON_WORKING_PERIOD,
                    schedulerRangeBegin.get(), schedulerRangeEnd.get(),
                    System.currentTimeMillis() + reminderInterval.get() * TimeUtils.MILLIS_IN_MINUTE);
        }

        if (scheduledTime == 0) {
            Timber.d("scheduleNextWakup: Schedule reminder for %1$d minutes",
                    reminderInterval.get());
            scheduleNextWakup(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + reminderInterval.get() * TimeUtils.MILLIS_IN_MINUTE);
        } else {
            Timber.d("scheduleNextWakup: Schedule reminder for time %1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS",
                    new Date(scheduledTime));
            scheduleNextWakup(AlarmManager.RTC_WAKEUP, scheduledTime);
        }
    }

    /**
     * Schedule wakup alarm for the sound notification pending intent
     *
     * @alarmType the type of the alarm either @{link AlarmManager#RTC_WAKEUP} or {link AlarmManager#ELAPSED_REALTIME_WAKEUP}
     * @time the next wakeup time
     */
    private void scheduleNextWakup(int alarmType, long time) {
        Timber.d("scheduleNextWakup: called");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mAlarmManager.setExactAndAllowWhileIdle(alarmType, time, mPendingIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mAlarmManager.setExact(alarmType, time, mPendingIntent);
        } else {
            mAlarmManager.set(alarmType, time, mPendingIntent);
        }
    }

    /**
     * Stop scheduled wakeup alarm for the periodical sound notification
     */
    private void stopWaking() {
        stopWaking(false);
    }

    /**
     * Stop scheduled wakeup alarm for the periodical sound notification
     *
     * @param force whether to do force cancel independently of the active flag value. Needed for active development
     *              when the pending intent may be changed or action scheduled by previous app run.
     */
    private void stopWaking(boolean force) {
        Timber.d("stopWaking");
        if (mActive.compareAndSet(true, false) || force) {
            Timber.d("stopWaking: cancel reminder");
            mAlarmManager.cancel(mPendingIntent);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Timber.d("onDestroy");
        // stop any scheduled alarms
        stopWaking();
        // unregister pending intent receiver
        unregisterReceiver(mPendingIntentReceiver);

        mSubscriptions.unsubscribe();
    }

    @Override
    public void onNotificationPosted() {
        Timber.d("onNotificationPosted");
        checkWakingConditions();
    }

    @Override
    public void onNotificationRemoved() {
        Timber.d("onNotificationRemoved");
        // stop alarm and check whether it should be launched again
        stopWaking();
        checkWakingConditions();
    }

    /**
     * The broadcast receiver for the pending intent action fired by alarm manager
     */
    class ScheduledSoundNotificationReceiver extends BroadcastReceiver {
        /**
         * Media player used to play notification sound
         */
        MediaPlayer mMediaPlayer = null;
        /**
         * The lock used during media player preparation and notification sound play
         * to make sure onReceive method will be running for some time keeping the device
         * awake
         */
        CountDownLatch mLock;

        ScheduledSoundNotificationReceiver() {
            // initialize media player
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
            mMediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            mMediaPlayer.setOnPreparedListener(__ -> Timber.d("MediaPlayer prepared"));
            mMediaPlayer.setOnCompletionListener(__ -> Timber.d("MediaPlayer completed playing")
            );
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Timber.d("onReceive");
            if (!mActive.get()) {
                Timber.w("onReceive: Invalid service activity state, stopping reminder");
                stopWaking(true);
                return;
            }
            if (!isScreenOn(context)) {
                Timber.d("onReceive: The screen is off, notify");
                try {
                    if (mMediaPlayer.isPlaying()) {
                        Timber.d("onReceive: Media player is playing. Stopping...");
                        mMediaPlayer.stop();
                    }
                    mMediaPlayer.reset();
                    // create lock object with timeout
                    mLock = new CountDownLatch(1);
                    Timber.d("onReceive: current thread %1$s", Thread.currentThread().getName());
                    Observable.just(mMediaPlayer)
                            .observeOn(Schedulers.io())
                            .subscribe(mp -> {
                                try {
                                    Timber.d("onReceive subscription: current thread %1$s", Thread.currentThread().getName());
                                    // get the default notification sound URI
                                    Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                                    mp.setDataSource(getApplicationContext(), notification);
                                    mp.prepare();
                                    mp.start();
                                    mLock.countDown();
                                } catch (Exception ex) {
                                    throw OnErrorThrowable.from(ex);
                                }
                            }, ex -> Timber.e(ex, null));
                    // give max 5 seconds to play notification sound
                    mLock.await(5, TimeUnit.SECONDS);
                    if (mLock.getCount() > 0) {
                        Timber.w("onReceive: media player initializes too long, didn't receive onComplete for 5 seconds.");
                    }
                } catch (Exception ex) {
                    Timber.e(ex, null);
                    throw new RuntimeException(ex);
                }
            } else {
                Timber.d("onReceive: The screen is on, skip notification");
            }
            scheduleNextWakup();
        }

        /**
         * Is the screen of the device on.
         *
         * @param context the context
         * @return true when (at least one) screen is on
         */
        public boolean isScreenOn(Context context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
                boolean screenOn = false;
                for (Display display : dm.getDisplays()) {
                    if (display.getState() != Display.STATE_OFF) {
                        screenOn = true;
                    }
                }
                return screenOn;
            } else {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                //noinspection deprecation
                return pm.isScreenOn();
            }
        }
    }

}
