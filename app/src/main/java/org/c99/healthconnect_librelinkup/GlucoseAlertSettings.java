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
import android.util.Log;

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

    public static final String PREFS_NAME = "glucose_alert_settings";
    public static final String KEY_LOW_ENABLED = "low_enabled";
    public static final String KEY_HIGH_ENABLED = "high_enabled";
    public static final String KEY_LOW_THRESHOLD_MGDL = "low_threshold_mgdl";
    public static final String KEY_HIGH_THRESHOLD_MGDL = "high_threshold_mgdl";
    public static final String KEY_LOW_REPEAT_ENABLED = "low_repeat_enabled";
    public static final String KEY_HIGH_REPEAT_ENABLED = "high_repeat_enabled";
    public static final String KEY_LOW_REPEAT_INTERVAL_MINUTES = "low_repeat_interval_minutes";
    public static final String KEY_HIGH_REPEAT_INTERVAL_MINUTES = "high_repeat_interval_minutes";
    public static final String KEY_LOW_HYSTERESIS_MGDL = "low_hysteresis_mgdl";
    public static final String KEY_HIGH_HYSTERESIS_MGDL = "high_hysteresis_mgdl";
    public static final String KEY_DISPLAY_UNITS = "display_units";
    private static final String KEY_UPDATED_AT = "updated_at";

    public static final String UNITS_MMOL = "mmol";
    public static final String UNITS_MGDL = "mgdl";

    public static final float DEFAULT_LOW_THRESHOLD_MGDL = 70f;
    public static final float DEFAULT_HIGH_THRESHOLD_MGDL = 180f;
    public static final float DEFAULT_HYSTERESIS_MGDL = 5f;
    public static final int DEFAULT_REPEAT_INTERVAL_MINUTES = 15;

    private final Context context;
    private final SharedPreferences preferences;

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
            boolean lowRepeatEnabled,
            int lowRepeatIntervalMinutes,
            float lowHysteresisMgDl,
            boolean highEnabled,
            float highThresholdMgDl,
            boolean highRepeatEnabled,
            int highRepeatIntervalMinutes,
            float highHysteresisMgDl,
            String displayUnits) {
        preferences.edit()
                .putBoolean(KEY_LOW_ENABLED, lowEnabled)
                .putBoolean(KEY_HIGH_ENABLED, highEnabled)
                .putFloat(KEY_LOW_THRESHOLD_MGDL, lowThresholdMgDl)
                .putFloat(KEY_HIGH_THRESHOLD_MGDL, highThresholdMgDl)
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

        sendToWear();
    }

    public void sendToWear() {
        PutDataMapRequest request = PutDataMapRequest.create(SETTINGS_PATH);
        request.getDataMap().putBoolean(KEY_LOW_ENABLED, isLowEnabled());
        request.getDataMap().putBoolean(KEY_HIGH_ENABLED, isHighEnabled());
        request.getDataMap().putFloat(KEY_LOW_THRESHOLD_MGDL, getLowThresholdMgDl());
        request.getDataMap().putFloat(KEY_HIGH_THRESHOLD_MGDL, getHighThresholdMgDl());
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
        request.getDataMap().putLong(KEY_UPDATED_AT, System.currentTimeMillis());

        PutDataRequest putDataRequest = request.asPutDataRequest().setUrgent();
        DataClient dataClient = Wearable.getDataClient(context);
        dataClient.putDataItem(putDataRequest)
                .addOnSuccessListener(dataItem -> Log.i(
                        TAG,
                        "Alert settings queued for Wear: low=" + isLowEnabled()
                                + " threshold=" + getLowThresholdMgDl()
                                + "mg/dL repeat=" + isLowRepeatEnabled()
                                + "/" + getLowRepeatIntervalMinutes() + "m"
                                + " hysteresis=" + getLowHysteresisMgDl()
                                + "mg/dL high=" + isHighEnabled()
                                + " threshold=" + getHighThresholdMgDl()
                                + "mg/dL repeat=" + isHighRepeatEnabled()
                                + "/" + getHighRepeatIntervalMinutes() + "m"
                                + " hysteresis=" + getHighHysteresisMgDl() + "mg/dL"
                ))
                .addOnFailureListener(exception -> Log.e(
                        TAG,
                        "Failed to send alert settings to Wear",
                        exception
                ));
    }

    private static int sanitizeRepeatInterval(int minutes) {
        switch (minutes) {
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
