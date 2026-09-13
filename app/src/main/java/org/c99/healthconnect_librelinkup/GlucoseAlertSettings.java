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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * Stores phone-side glucose alert preferences and mirrors them to the paired Wear OS app.
 * Thresholds and hysteresis margins are stored and transferred in mg/dL so the watch can
 * evaluate them consistently regardless of the display units selected in the phone UI or
 * supplied by LibreLinkUp.
 */
public final class GlucoseAlertSettings {
    private static final String TAG = "LibreLinkUpAlert";
    private static final String SETTINGS_PATH = "/alert-settings";
    public static final String SETTINGS_ACK_PATH = "/alert-settings-ack";

    public static final String PREFS_NAME = "glucose_alert_settings";
    public static final String KEY_LOW_ENABLED = "low_enabled";
    public static final String KEY_HIGH_ENABLED = "high_enabled";
    public static final String KEY_LOW_THRESHOLD_MGDL = "low_threshold_mgdl";
    public static final String KEY_HIGH_THRESHOLD_MGDL = "high_threshold_mgdl";
    public static final String KEY_LOW_PERSISTENT_VIBRATION = "low_persistent_vibration";
    public static final String KEY_HIGH_PERSISTENT_VIBRATION = "high_persistent_vibration";
    public static final String KEY_LOW_REPEAT_ENABLED = "low_repeat_enabled";
    public static final String KEY_HIGH_REPEAT_ENABLED = "high_repeat_enabled";
    public static final String KEY_LOW_REPEAT_INTERVAL_MINUTES = "low_repeat_interval_minutes";
    public static final String KEY_HIGH_REPEAT_INTERVAL_MINUTES = "high_repeat_interval_minutes";
    public static final String KEY_LOW_HYSTERESIS_MGDL = "low_hysteresis_mgdl";
    public static final String KEY_HIGH_HYSTERESIS_MGDL = "high_hysteresis_mgdl";
    public static final String KEY_DISPLAY_UNITS = "display_units";
    public static final String KEY_REQUEST_ID = "request_id";
    private static final String KEY_UPDATED_AT = "updated_at";
    private static final String KEY_PENDING_REQUEST_ID = "pending_request_id";
    private static final String KEY_PENDING_SENT_AT_MS = "pending_sent_at_ms";
    private static final String KEY_LAST_ACK_REQUEST_ID = "last_ack_request_id";
    private static final String KEY_LAST_ACK_AT_MS = "last_ack_at_ms";
    private static final String KEY_SEND_FAILED_REQUEST_ID = "send_failed_request_id";
    private static final String KEY_NOTIFY_REQUEST_ID = "notify_request_id";

    public static final String UNITS_MMOL = "mmol";
    public static final String UNITS_MGDL = "mgdl";

    public static final float DEFAULT_LOW_THRESHOLD_MGDL = 70f;
    public static final float DEFAULT_HIGH_THRESHOLD_MGDL = 180f;
    public static final float DEFAULT_HYSTERESIS_MGDL = 5f;
    public static final int DEFAULT_REPEAT_INTERVAL_MINUTES = 15;
    public static final long WATCH_ACK_TIMEOUT_MS = 15_000L;

    public enum WatchSyncStatus {
        IDLE,
        WAITING,
        CONFIRMED,
        NOT_CONFIRMED,
        SEND_FAILED
    }

    private final Context context;
    private final SharedPreferences preferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public GlucoseAlertSettings(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isLowEnabled() {
        return preferences.getBoolean(KEY_LOW_ENABLED, false);
    }

    public boolean isHighEnabled() {
        return preferences.getBoolean(KEY_HIGH_ENABLED, false);
    }

    public float getLowThresholdMgDl() {
        return preferences.getFloat(KEY_LOW_THRESHOLD_MGDL, DEFAULT_LOW_THRESHOLD_MGDL);
    }

    public float getHighThresholdMgDl() {
        return preferences.getFloat(KEY_HIGH_THRESHOLD_MGDL, DEFAULT_HIGH_THRESHOLD_MGDL);
    }

    public boolean isLowPersistentVibrationEnabled() {
        return preferences.getBoolean(KEY_LOW_PERSISTENT_VIBRATION, false);
    }

    public boolean isHighPersistentVibrationEnabled() {
        return preferences.getBoolean(KEY_HIGH_PERSISTENT_VIBRATION, false);
    }

    public boolean isLowRepeatEnabled() {
        return preferences.getBoolean(KEY_LOW_REPEAT_ENABLED, false);
    }

    public boolean isHighRepeatEnabled() {
        return preferences.getBoolean(KEY_HIGH_REPEAT_ENABLED, false);
    }

    public int getLowRepeatIntervalMinutes() {
        return sanitizeRepeatInterval(
                preferences.getInt(
                        KEY_LOW_REPEAT_INTERVAL_MINUTES,
                        DEFAULT_REPEAT_INTERVAL_MINUTES
                )
        );
    }

    public int getHighRepeatIntervalMinutes() {
        return sanitizeRepeatInterval(
                preferences.getInt(
                        KEY_HIGH_REPEAT_INTERVAL_MINUTES,
                        DEFAULT_REPEAT_INTERVAL_MINUTES
                )
        );
    }

    public float getLowHysteresisMgDl() {
        return Math.max(
                0f,
                preferences.getFloat(KEY_LOW_HYSTERESIS_MGDL, DEFAULT_HYSTERESIS_MGDL)
        );
    }

    public float getHighHysteresisMgDl() {
        return Math.max(
                0f,
                preferences.getFloat(KEY_HIGH_HYSTERESIS_MGDL, DEFAULT_HYSTERESIS_MGDL)
        );
    }

    public String getDisplayUnits() {
        return preferences.getString(KEY_DISPLAY_UNITS, UNITS_MMOL);
    }

    public void saveAndSend(
            boolean lowEnabled,
            float lowThresholdMgDl,
            boolean lowPersistentVibration,
            boolean lowRepeatEnabled,
            int lowRepeatIntervalMinutes,
            float lowHysteresisMgDl,
            boolean highEnabled,
            float highThresholdMgDl,
            boolean highPersistentVibration,
            boolean highRepeatEnabled,
            int highRepeatIntervalMinutes,
            float highHysteresisMgDl,
            String displayUnits) {
        preferences.edit()
                .putBoolean(KEY_LOW_ENABLED, lowEnabled)
                .putBoolean(KEY_HIGH_ENABLED, highEnabled)
                .putFloat(KEY_LOW_THRESHOLD_MGDL, lowThresholdMgDl)
                .putFloat(KEY_HIGH_THRESHOLD_MGDL, highThresholdMgDl)
                .putBoolean(KEY_LOW_PERSISTENT_VIBRATION, lowPersistentVibration)
                .putBoolean(KEY_HIGH_PERSISTENT_VIBRATION, highPersistentVibration)
                .putBoolean(KEY_LOW_REPEAT_ENABLED, lowRepeatEnabled)
                .putBoolean(KEY_HIGH_REPEAT_ENABLED, highRepeatEnabled)
                .putInt(
                        KEY_LOW_REPEAT_INTERVAL_MINUTES,
                        sanitizeRepeatInterval(lowRepeatIntervalMinutes)
                )
                .putInt(
                        KEY_HIGH_REPEAT_INTERVAL_MINUTES,
                        sanitizeRepeatInterval(highRepeatIntervalMinutes)
                )
                .putFloat(KEY_LOW_HYSTERESIS_MGDL, Math.max(0f, lowHysteresisMgDl))
                .putFloat(KEY_HIGH_HYSTERESIS_MGDL, Math.max(0f, highHysteresisMgDl))
                .putString(KEY_DISPLAY_UNITS, displayUnits)
                .apply();

        sendToWearInternal(false, true);
    }

    public void retrySendToWear() {
        sendToWearInternal(false, true);
    }

    public void sendToWear() {
        sendToWearInternal(false, false);
    }

    private void sendToWearInternal(boolean automaticRetry, boolean userInitiated) {
        long now = System.currentTimeMillis();
        long previousRequestId = preferences.getLong(KEY_PENDING_REQUEST_ID, 0L);
        long requestId = Math.max(now, previousRequestId + 1L);

        SharedPreferences.Editor pendingEditor = preferences.edit()
                .putLong(KEY_PENDING_REQUEST_ID, requestId)
                .putLong(KEY_PENDING_SENT_AT_MS, now)
                .remove(KEY_SEND_FAILED_REQUEST_ID);
        if (userInitiated) {
            pendingEditor.putLong(KEY_NOTIFY_REQUEST_ID, requestId);
        }
        pendingEditor.apply();

        PutDataMapRequest request = PutDataMapRequest.create(SETTINGS_PATH);
        request.getDataMap().putBoolean(KEY_LOW_ENABLED, isLowEnabled());
        request.getDataMap().putBoolean(KEY_HIGH_ENABLED, isHighEnabled());
        request.getDataMap().putFloat(KEY_LOW_THRESHOLD_MGDL, getLowThresholdMgDl());
        request.getDataMap().putFloat(KEY_HIGH_THRESHOLD_MGDL, getHighThresholdMgDl());
        request.getDataMap().putBoolean(
                KEY_LOW_PERSISTENT_VIBRATION,
                isLowPersistentVibrationEnabled()
        );
        request.getDataMap().putBoolean(
                KEY_HIGH_PERSISTENT_VIBRATION,
                isHighPersistentVibrationEnabled()
        );
        request.getDataMap().putBoolean(KEY_LOW_REPEAT_ENABLED, isLowRepeatEnabled());
        request.getDataMap().putBoolean(KEY_HIGH_REPEAT_ENABLED, isHighRepeatEnabled());
        request.getDataMap().putInt(
                KEY_LOW_REPEAT_INTERVAL_MINUTES,
                getLowRepeatIntervalMinutes()
        );
        request.getDataMap().putInt(
                KEY_HIGH_REPEAT_INTERVAL_MINUTES,
                getHighRepeatIntervalMinutes()
        );
        request.getDataMap().putFloat(KEY_LOW_HYSTERESIS_MGDL, getLowHysteresisMgDl());
        request.getDataMap().putFloat(KEY_HIGH_HYSTERESIS_MGDL, getHighHysteresisMgDl());
        request.getDataMap().putLong(KEY_REQUEST_ID, requestId);
        request.getDataMap().putLong(KEY_UPDATED_AT, now);

        PutDataRequest putDataRequest = request.asPutDataRequest().setUrgent();
        DataClient dataClient = Wearable.getDataClient(context);
        dataClient.putDataItem(putDataRequest)
                .addOnSuccessListener(dataItem -> {
                    Log.i(
                            TAG,
                            "Alert settings queued for Wear requestId=" + requestId
                                    + ": low=" + isLowEnabled()
                                    + " threshold=" + getLowThresholdMgDl()
                                    + "mg/dL persistentVibration=" + isLowPersistentVibrationEnabled()
                                    + " repeat=" + isLowRepeatEnabled()
                                    + "/" + getLowRepeatIntervalMinutes() + "m"
                                    + " hysteresis=" + getLowHysteresisMgDl()
                                    + "mg/dL high=" + isHighEnabled()
                                    + " threshold=" + getHighThresholdMgDl()
                                    + "mg/dL persistentVibration=" + isHighPersistentVibrationEnabled()
                                    + " repeat=" + isHighRepeatEnabled()
                                    + "/" + getHighRepeatIntervalMinutes() + "m"
                                    + " hysteresis=" + getHighHysteresisMgDl() + "mg/dL"
                    );
                    scheduleConfirmationCheck(requestId, automaticRetry, userInitiated);
                })
                .addOnFailureListener(exception -> {
                    preferences.edit()
                            .putLong(KEY_SEND_FAILED_REQUEST_ID, requestId)
                            .apply();
                    Log.e(
                            TAG,
                            "Failed to queue alert settings for Wear requestId=" + requestId,
                            exception
                    );
                    if (userInitiated) {
                        mainHandler.post(() -> Toast.makeText(
                                context,
                                "Could not send alert settings to watch. Phone settings were kept.",
                                Toast.LENGTH_LONG
                        ).show());
                    }
                });
    }

    private void scheduleConfirmationCheck(
            long requestId,
            boolean automaticRetry,
            boolean userInitiated) {
        mainHandler.postDelayed(() -> {
            long pendingRequestId = preferences.getLong(KEY_PENDING_REQUEST_ID, 0L);
            if (pendingRequestId != requestId) return;
            if (preferences.getLong(KEY_LAST_ACK_REQUEST_ID, 0L) == requestId) return;

            if (!automaticRetry) {
                Log.w(TAG, "Wear did not confirm alert settings requestId=" + requestId + "; retrying once");
                if (userInitiated) {
                    Toast.makeText(
                            context,
                            "Watch not confirmed. Retrying alert settings...",
                            Toast.LENGTH_LONG
                    ).show();
                }
                sendToWearInternal(true, userInitiated);
            } else {
                Log.w(TAG, "Wear did not confirm alert settings after retry requestId=" + requestId);
                if (userInitiated) {
                    Toast.makeText(
                            context,
                            "Watch not confirmed. Phone settings were kept; the watch may still be using its previous settings.",
                            Toast.LENGTH_LONG
                    ).show();
                }
            }
        }, WATCH_ACK_TIMEOUT_MS);
    }

    public boolean recordWatchAcknowledgement(long requestId) {
        if (requestId <= 0L) return false;

        long lastAckRequestId = preferences.getLong(KEY_LAST_ACK_REQUEST_ID, 0L);
        if (requestId < lastAckRequestId) {
            Log.i(TAG, "Ignoring stale Wear acknowledgement requestId=" + requestId);
            return false;
        }

        long pendingRequestId = preferences.getLong(KEY_PENDING_REQUEST_ID, 0L);
        boolean currentRequest = requestId == pendingRequestId;
        boolean notifyUser = currentRequest &&
                preferences.getLong(KEY_NOTIFY_REQUEST_ID, 0L) == requestId;

        SharedPreferences.Editor editor = preferences.edit()
                .putLong(KEY_LAST_ACK_REQUEST_ID, requestId)
                .putLong(KEY_LAST_ACK_AT_MS, System.currentTimeMillis());
        if (notifyUser) editor.remove(KEY_NOTIFY_REQUEST_ID);
        editor.apply();

        if (currentRequest) {
            Log.i(TAG, "Alert settings confirmed on Wear requestId=" + requestId);
        } else {
            Log.i(
                    TAG,
                    "Wear acknowledged older alert settings requestId=" + requestId
                            + " while pending requestId=" + pendingRequestId
            );
        }
        return notifyUser;
    }

    public WatchSyncStatus getWatchSyncStatus() {
        long pendingRequestId = preferences.getLong(KEY_PENDING_REQUEST_ID, 0L);
        if (pendingRequestId <= 0L) return WatchSyncStatus.IDLE;

        if (preferences.getLong(KEY_LAST_ACK_REQUEST_ID, 0L) == pendingRequestId) {
            return WatchSyncStatus.CONFIRMED;
        }
        if (preferences.getLong(KEY_SEND_FAILED_REQUEST_ID, 0L) == pendingRequestId) {
            return WatchSyncStatus.SEND_FAILED;
        }

        long sentAt = preferences.getLong(KEY_PENDING_SENT_AT_MS, 0L);
        if (sentAt > 0L && System.currentTimeMillis() - sentAt >= WATCH_ACK_TIMEOUT_MS) {
            return WatchSyncStatus.NOT_CONFIRMED;
        }
        return WatchSyncStatus.WAITING;
    }

    private static int sanitizeRepeatInterval(int minutes) {
        switch (minutes) {
            case 1:
            case 2:
            case 5:
            case 10:
            case 15:
            case 30:
            case 60:
                return minutes;
            default:
                return DEFAULT_REPEAT_INTERVAL_MINUTES;
        }
    }
}
