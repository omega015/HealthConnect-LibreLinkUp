/*
 * Copyright (c) 2024 Sam Steele
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.c99.healthconnect_librelinkup;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class FastSyncService extends Service {
    public static final String ACTION_START = "org.c99.healthconnect_librelinkup.action.START_FAST_SYNC";
    public static final String ACTION_STOP = "org.c99.healthconnect_librelinkup.action.STOP_FAST_SYNC";

    private static final String TAG = "LibreLinkUpSync";
    private static final String CHANNEL_ID = "fast_glucose_sync";
    private static final int NOTIFICATION_ID = 1001;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> syncTask;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;

        if (ACTION_STOP.equals(action)) {
            stopFastSync();
            return START_NOT_STICKY;
        }

        LibreLinkUp libreLinkUp = new LibreLinkUp(this);
        if (!LibreLinkUp.SYNC_MODE_FAST.equals(libreLinkUp.getSyncMode())
                || libreLinkUp.getAuthTicket() == null
                || libreLinkUp.getAuthTicket().token == null
                || libreLinkUp.getAuthTicket().token.isEmpty()) {
            stopFastSync();
            return START_NOT_STICKY;
        }

        int intervalMinutes = libreLinkUp.getFastSyncIntervalMinutes();
        startForeground(
                NOTIFICATION_ID,
                buildNotification("Fast glucose sync every " + intervalMinutes + " minutes")
        );
        restartSchedule(intervalMinutes);
        return START_STICKY;
    }

    private synchronized void restartSchedule(int intervalMinutes) {
        if (syncTask != null) {
            syncTask.cancel(false);
        }

        syncTask = scheduler.scheduleWithFixedDelay(
                () -> runSyncSafely(intervalMinutes),
                0,
                intervalMinutes,
                TimeUnit.MINUTES
        );

        Log.i(TAG, "Foreground fast sync scheduled every " + intervalMinutes + " minutes");
    }

    private void runSyncSafely(int intervalMinutes) {
        try {
            runSync(intervalMinutes);
        } catch (Exception e) {
            // ScheduledExecutorService suppresses future executions when a task throws.
            // Keep Fast mode alive after an unexpected per-sync application failure.
            Log.e(TAG, "Unexpected fast sync failure; scheduler will continue", e);
        }
    }

    private void runSync(int intervalMinutes) {
        GlucoseSync.SyncResult result = GlucoseSync.perform(this);
        String status;
        if (result.success) {
            status = "Fast glucose sync every " + intervalMinutes + " minutes • last sync successful";
        } else {
            status = "Fast glucose sync every " + intervalMinutes + " minutes • last sync failed";
        }

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.notify(NOTIFICATION_ID, buildNotification(status));
    }

    private Notification buildNotification(String text) {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle("LibreLinkUp fast sync")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Fast glucose sync",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps LibreLinkUp glucose updates running at the selected fast interval");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private synchronized void stopFastSync() {
        if (syncTask != null) {
            syncTask.cancel(false);
            syncTask = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (syncTask != null) {
            syncTask.cancel(false);
            syncTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
