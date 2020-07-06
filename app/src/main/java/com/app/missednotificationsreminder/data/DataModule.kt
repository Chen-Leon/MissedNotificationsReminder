package com.app.missednotificationsreminder.data

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Vibrator
import com.app.missednotificationsreminder.R
import com.app.missednotificationsreminder.di.qualifiers.*
import com.app.missednotificationsreminder.service.data.model.NotificationData
import com.app.missednotificationsreminder.service.event.NotificationsUpdatedEvent
import com.app.missednotificationsreminder.service.event.RemindEvents
import com.app.missednotificationsreminder.settings.applicationselection.data.model.util.ApplicationIconHandler
import com.app.missednotificationsreminder.util.event.Event
import com.app.missednotificationsreminder.util.event.RxEventBus
import com.f2prateek.rx.preferences.Preference
import com.f2prateek.rx.preferences.RxSharedPreferences
import com.squareup.picasso.Picasso
import dagger.Module
import dagger.Provides
import rx.Completable
import rx.Observable
import rx.Scheduler
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * The Dagger dependency injection module for the data layer
 */
@Module
class DataModule {
    @Provides
    @Singleton
    @MainThreadScheduler
    fun provideMainThreadScheduler(): Scheduler {
        return AndroidSchedulers.mainThread()
    }

    @Provides
    @Singleton
    @IoThreadScheduler
    fun provideIoThreadScheduler(): Scheduler {
        return Schedulers.io()
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(app: Application): SharedPreferences {
        return app.getSharedPreferences("missingnotificationreminder", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideRxSharedPreferences(prefs: SharedPreferences): RxSharedPreferences {
        return RxSharedPreferences.create(prefs)
    }

    @Provides
    @Singleton
    @ReminderIntervalMax
    fun provideReminderIntervalMaximum(@ForApplication context: Context): Int {
        return context.resources.getInteger(R.integer.reminderIntervalMaximum)
    }

    @Provides
    @Singleton
    @ReminderIntervalMin
    fun provideReminderIntervalMinimum(@ForApplication context: Context): Int {
        return context.resources.getInteger(R.integer.reminderIntervalMinimum)
    }

    @Provides
    @Singleton
    @ReminderIntervalDefault
    fun provideReminderIntervalDefault(@ForApplication context: Context): Int {
        return context.resources.getInteger(R.integer.reminderIntervalDefault)
    }

    @Provides
    @Singleton
    @LimitReminderRepeats
    fun provideLimitReminderRepeats(prefs: RxSharedPreferences): Preference<Boolean> {
        return prefs.getBoolean("LIMIT_REMINDER_REPEATS", false)
    }

    @Provides
    @Singleton
    @ReminderInterval
    fun provideReminderInterval(prefs: RxSharedPreferences, @ReminderIntervalDefault reminderIntervalDefault: Int): Preference<Int> {
        return prefs.getInteger("REMINDER_INTERVAL", reminderIntervalDefault)
    }

    @Provides
    @Singleton
    @ReminderRepeats
    fun provideReminderRepeats(prefs: RxSharedPreferences, @ReminderRepeatsDefault reminderRepeatsDefault: Int): Preference<Int> {
        return prefs.getInteger("REMINDER_REPEATS", reminderRepeatsDefault)
    }

    @Provides
    @Singleton
    @ReminderRepeatsDefault
    fun provideReminderRepeatsDefault(@ForApplication context: Context): Int {
        return context.resources.getInteger(R.integer.reminderRepeatsDefault)
    }

    @Provides
    @Singleton
    @ReminderRepeatsMax
    fun provideReminderRepeatsMaximum(@ForApplication context: Context): Int {
        return context.resources.getInteger(R.integer.reminderRepeatsMaximum)
    }

    @Provides
    @Singleton
    @ReminderRepeatsMin
    fun provideReminderRepeatsMinimum(@ForApplication context: Context): Int {
        return context.resources.getInteger(R.integer.reminderRepeatsMinimum)
    }

    @Provides
    @Singleton
    @CreateDismissNotification
    fun provideCreateDismissNotification(prefs: RxSharedPreferences): Preference<Boolean> {
        return prefs.getBoolean("CREATE_DISMISS_NOTIFICATION", true)
    }

    @Provides
    @Singleton
    @CreateDismissNotificationImmediately
    fun provideCreateDismissNotificationImmediately(prefs: RxSharedPreferences): Preference<Boolean> {
        return prefs.getBoolean("CREATE_DISMISS_NOTIFICATION_IMMEDIATELY", true)
    }

    @Provides
    @Singleton
    @ForceWakeLock
    fun provideForceWakeLock(prefs: RxSharedPreferences): Preference<Boolean> {
        return prefs.getBoolean(ForceWakeLock::class.java.simpleName, false)
    }

    @Provides
    @Singleton
    @ReminderRingtone
    fun provideReminderRingtone(prefs: RxSharedPreferences): Preference<String> {
        val defaultRingtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        return prefs.getString("REMINDER_RINGTONE", defaultRingtone?.toString() ?: "")
    }

    @Provides
    @Singleton
    @Vibrate
    fun provideVibrate(prefs: RxSharedPreferences, vibrator: Vibrator): Preference<Boolean> {
        return prefs.getBoolean(Vibrate::class.java.simpleName, vibrator.hasVibrator())
    }

    @Provides
    @Singleton
    @VibrationPatternDefault
    fun provideVibrationPatternDefault(@ForApplication context: Context): String {
        return context.resources.getString(R.string.vibrationPatternDefault)
    }

    @Provides
    @Singleton
    @VibrationPattern
    fun provideVibrationPattern(prefs: RxSharedPreferences, @VibrationPatternDefault vibrationPatternDefault: String?): Preference<String> {
        return prefs.getString("VIBRATION_PATTERN", vibrationPatternDefault)
    }

    @Provides
    @Singleton
    @SelectedApplications
    fun provideSelectedApplications(prefs: RxSharedPreferences): Preference<Set<String>> {
        return prefs.getStringSet("SELECTED_APPLICATIONS")
    }

    @Provides
    @Singleton
    @IgnorePersistentNotifications
    fun provideIgnorePersistentNotifications(prefs: RxSharedPreferences): Preference<Boolean> {
        return prefs.getBoolean(IgnorePersistentNotifications::class.java.name, true)
    }

    @Provides
    @Singleton
    @RespectPhoneCalls
    fun provideRespectPhoneCalls(prefs: RxSharedPreferences): Preference<Boolean> {
        return prefs.getBoolean(RespectPhoneCalls::class.java.name, true)
    }

    @Provides
    @Singleton
    @RespectRingerMode
    fun provideRespectRingerMode(prefs: RxSharedPreferences): Preference<Boolean> {
        return prefs.getBoolean(RespectRingerMode::class.java.name, true)
    }

    @Provides
    @Singleton
    @RemindWhenScreenIsOn
    fun provideRemindWhenScreenIsOn(prefs: RxSharedPreferences): Preference<Boolean> {
        return prefs.getBoolean(RemindWhenScreenIsOn::class.java.name, true)
    }

    @Provides
    @Singleton
    @ReminderEnabled
    fun provideReminderEnabled(prefs: RxSharedPreferences): Preference<Boolean> {
        return prefs.getBoolean("REMINDER_ENABLED", true)
    }

    @Provides
    @Singleton
    @SchedulerEnabled
    fun provideSchedulerEnabled(prefs: RxSharedPreferences): Preference<Boolean> {
        return prefs.getBoolean("SCHEDULER_ENABLED", false)
    }

    @Provides
    @Singleton
    @SchedulerMode
    fun provideSchedulerMode(prefs: RxSharedPreferences): Preference<Boolean> {
        return prefs.getBoolean("SCHEDULER_MODE", true)
    }

    @Provides
    @Singleton
    @SchedulerRangeMax
    fun provideSchedulerRangeMaximum(@ForApplication context: Context): Int {
        return context.resources.getInteger(R.integer.schedulerRangeMaximum)
    }

    @Provides
    @Singleton
    @SchedulerRangeMin
    fun provideSchedulerRangeMinimum(@ForApplication context: Context): Int {
        return context.resources.getInteger(R.integer.schedulerRangeMinimum)
    }

    @Provides
    @Singleton
    @SchedulerRangeDefaultBegin
    fun provideSchedulerDefaultBegin(@ForApplication context: Context): Int {
        return context.resources.getInteger(R.integer.schedulerRangeDefaultBegin)
    }

    @Provides
    @Singleton
    @SchedulerRangeDefaultEnd
    fun provideSchedulerDefaultEnd(@ForApplication context: Context): Int {
        return context.resources.getInteger(R.integer.schedulerRangeDefaultEnd)
    }

    @Provides
    @Singleton
    @SchedulerRangeBegin
    fun provideSchedulerRangeBegin(prefs: RxSharedPreferences, @SchedulerRangeDefaultBegin schedulerRangeDefaultBegin: Int): Preference<Int> {
        return prefs.getInteger("SCHEDULER_RANGE_BEGIN", schedulerRangeDefaultBegin)
    }

    @Provides
    @Singleton
    @SchedulerRangeEnd
    fun provideSchedulerRangeEnd(prefs: RxSharedPreferences, @SchedulerRangeDefaultEnd schedulerRangeDefaultEnd: Int): Preference<Int> {
        return prefs.getInteger("SCHEDULER_RANGE_END", schedulerRangeDefaultEnd)
    }

    @Provides
    @Singleton
    fun providePackageManager(app: Application): PackageManager {
        return app.packageManager
    }

    @Provides
    @Singleton
    fun providePicasso(app: Application, packageManager: PackageManager): Picasso {
        return Picasso.Builder(app)
                .addRequestHandler(ApplicationIconHandler(packageManager))
                .listener { _, uri, e -> Timber.e(e, "Failed to load image: %s", uri) }
                .build()
    }

    @Provides
    @Singleton
    fun provideEventBus(): RxEventBus {
        return RxEventBus()
    }

    @Provides
    @Singleton
    fun provideNotificationDataObservable(eventBus: RxEventBus): Observable<List<NotificationData>> {
        Timber.d("provideNotificationDataObservable() called with: eventBus = %s",
                eventBus)
        return eventBus.toObserverable()
                .filter { event: Event? -> event is NotificationsUpdatedEvent }
                .map { event: Event -> (event as NotificationsUpdatedEvent).notifications }
                .startWith(emptyList<NotificationData>())
                .mergeWith(Completable.fromAction { eventBus.send(RemindEvents.GET_CURRENT_NOTIFICATIONS_DATA) }.toObservable())
                .replay(1)
                .refCount()
                .doOnNext { data: List<NotificationData?> -> Timber.d("notificationDataObservable: %d", data.size) }
                .debounce(500, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
    }

}