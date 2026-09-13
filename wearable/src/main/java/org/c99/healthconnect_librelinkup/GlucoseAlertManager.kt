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

package org.c99.healthconnect_librelinkup

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * Evaluates each new glucose reading on the watch and raises local alerts.
 * Alert state and timing live on the watch so repeats and re-arm behaviour continue to work
 * independently once settings have been received from the phone.
 */
object GlucoseAlertManager {
    private const val TAG = "LibreLinkUpAlert"

    const val PREFS_NAME = "glucose_alert_settings"
    const val KEY_LOW_ENABLED = "low_enabled"
    const val KEY_HIGH_ENABLED = "high_enabled"
    const val KEY_LOW_THRESHOLD_MGDL = "low_threshold_mgdl"
    const val KEY_HIGH_THRESHOLD_MGDL = "high_threshold_mgdl"
    const val KEY_LOW_REPEAT_ENABLED = "low_repeat_enabled"
    const val KEY_HIGH_REPEAT_ENABLED = "high_repeat_enabled"
    const val KEY_LOW_REPEAT_INTERVAL_MINUTES = "low_repeat_interval_minutes"
    const val KEY_HIGH_REPEAT_INTERVAL_MINUTES = "high_repeat_interval_minutes"
    const val KEY_LOW_HYSTERESIS_MGDL = "low_hysteresis_mgdl"
    const val KEY_HIGH_HYSTERESIS_MGDL = "high_hysteresis_mgdl"

    private const val KEY_ALERT_STATE = "alert_state"
    private const val KEY_LAST_LOW_ALERT_TIME_MS = "last_low_alert_time_ms"
    private const val KEY_LAST_HIGH_ALERT_TIME_MS = "last_high_alert_time_ms"

    private const val STATE_NORMAL = "normal"
    private const val STATE_LOW = "low"
    private const val STATE_HIGH = "high"

    private const val DEFAULT_LOW_THRESHOLD_MGDL = 70f
    private const val DEFAULT_HIGH_THRESHOLD_MGDL = 180f
    private const val DEFAULT_HYSTERESIS_MGDL = 5f
    private const val DEFAULT_REPEAT_INTERVAL_MINUTES = 15

    private const val LOW_CHANNEL_ID = "low_glucose_alerts"
    private const val HIGH_CHANNEL_ID = "high_glucose_alerts"
    private const val LOW_NOTIFICATION_ID = 2001
    private const val HIGH_NOTIFICATION_ID = 2002

    fun evaluate(context: Context, glucoseValue: Float, glucoseUnits: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lowEnabled = prefs.getBoolean(KEY_LOW_ENABLED, false)
        val highEnabled = prefs.getBoolean(KEY_HIGH_ENABLED, false)

        if (!lowEnabled && !highEnabled) return

        val glucoseMgDl = if (glucoseUnits == 1) glucoseValue else glucoseValue * 18f
        val lowThreshold = prefs.getFloat(KEY_LOW_THRESHOLD_MGDL, DEFAULT_LOW_THRESHOLD_MGDL)
        val highThreshold = prefs.getFloat(KEY_HIGH_THRESHOLD_MGDL, DEFAULT_HIGH_THRESHOLD_MGDL)
        val lowHysteresis = prefs.getFloat(KEY_LOW_HYSTERESIS_MGDL, DEFAULT_HYSTERESIS_MGDL)
            .coerceAtLeast(0f)
        val highHysteresis = prefs.getFloat(KEY_HIGH_HYSTERESIS_MGDL, DEFAULT_HYSTERESIS_MGDL)
            .coerceAtLeast(0f)
        val previousState = prefs.getString(KEY_ALERT_STATE, STATE_NORMAL) ?: STATE_NORMAL

        val nextState = when {
            lowEnabled && glucoseMgDl <= lowThreshold -> STATE_LOW
            highEnabled && glucoseMgDl >= highThreshold -> STATE_HIGH
            previousState == STATE_LOW && lowEnabled && glucoseMgDl < lowThreshold + lowHysteresis -> STATE_LOW
            previousState == STATE_HIGH && highEnabled && glucoseMgDl > highThreshold - highHysteresis -> STATE_HIGH
            else -> STATE_NORMAL
        }

        val editor = prefs.edit()
        if (nextState != previousState) {
            editor.putString(KEY_ALERT_STATE, nextState)
            if (nextState == STATE_LOW) editor.remove(KEY_LAST_LOW_ALERT_TIME_MS)
            if (nextState == STATE_HIGH) editor.remove(KEY_LAST_HIGH_ALERT_TIME_MS)
            if (nextState == STATE_NORMAL) {
                editor.remove(KEY_LAST_LOW_ALERT_TIME_MS)
                editor.remove(KEY_LAST_HIGH_ALERT_TIME_MS)
            }
            editor.apply()
        }

        when (nextState) {
            STATE_LOW -> maybeAlert(
                context,
                low = true,
                glucoseValue = glucoseValue,
                glucoseUnits = glucoseUnits,
                enteredState = previousState != STATE_LOW
            )
            STATE_HIGH -> maybeAlert(
                context,
                low = false,
                glucoseValue = glucoseValue,
                glucoseUnits = glucoseUnits,
                enteredState = previousState != STATE_HIGH
            )
            STATE_NORMAL -> {
                if (previousState != STATE_NORMAL) {
                    val notificationManager = context.getSystemService(NotificationManager::class.java)
                    notificationManager.cancel(LOW_NOTIFICATION_ID)
                    notificationManager.cancel(HIGH_NOTIFICATION_ID)
                    Log.i(TAG, "Glucose returned to alert re-arm range")
                }
            }
        }
    }

    private fun maybeAlert(
        context: Context,
        low: Boolean,
        glucoseValue: Float,
        glucoseUnits: Int,
        enteredState: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val repeatEnabled = prefs.getBoolean(
            if (low) KEY_LOW_REPEAT_ENABLED else KEY_HIGH_REPEAT_ENABLED,
            false
        )
        val repeatMinutes = sanitizeRepeatInterval(
            prefs.getInt(
                if (low) KEY_LOW_REPEAT_INTERVAL_MINUTES else KEY_HIGH_REPEAT_INTERVAL_MINUTES,
                DEFAULT_REPEAT_INTERVAL_MINUTES
            )
        )
        val lastAlertKey = if (low) KEY_LAST_LOW_ALERT_TIME_MS else KEY_LAST_HIGH_ALERT_TIME_MS
        val lastAlertTime = prefs.getLong(lastAlertKey, 0L)
        val now = System.currentTimeMillis()
        val repeatDue = repeatEnabled && lastAlertTime > 0L &&
            now - lastAlertTime >= repeatMinutes * 60_000L
        val shouldAlert = enteredState || lastAlertTime == 0L || repeatDue

        if (!shouldAlert) return

        if (showAlert(context, low, glucoseValue, glucoseUnits)) {
            prefs.edit().putLong(lastAlertKey, now).apply()
            if (repeatDue) {
                Log.i(TAG, (if (low) "Low" else "High") + " glucose repeat alert posted")
            }
        }
    }

    private fun showAlert(
        context: Context,
        low: Boolean,
        glucoseValue: Float,
        glucoseUnits: Int
    ): Boolean {
        createNotificationChannels(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Glucose alert suppressed because watch notification permission is not granted")
            return false
        }

        val channelId = if (low) LOW_CHANNEL_ID else HIGH_CHANNEL_ID
        val notificationId = if (low) LOW_NOTIFICATION_ID else HIGH_NOTIFICATION_ID
        val title = context.getString(
            if (low) R.string.low_glucose_alert_title else R.string.high_glucose_alert_title
        )
        val formattedValue = if (glucoseUnits == 1) {
            String.format("%.0f mg/dL", glucoseValue)
        } else {
            String.format("%.1f mmol/L", glucoseValue)
        }
        val message = context.getString(R.string.glucose_alert_value, formattedValue)

        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(notificationId, notification)

        Log.i(TAG, (if (low) "Low" else "High") + " glucose alert posted: " + formattedValue)
        return true
    }

    private fun sanitizeRepeatInterval(minutes: Int): Int = when (minutes) {
        1, 2, 5, 10, 15, 30, 60 -> minutes
        else -> DEFAULT_REPEAT_INTERVAL_MINUTES
    }

    private fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(NotificationManager::class.java)

        val lowChannel = NotificationChannel(
            LOW_CHANNEL_ID,
            context.getString(R.string.low_glucose_alert_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.low_glucose_alert_channel_description)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 750)
        }

        val highChannel = NotificationChannel(
            HIGH_CHANNEL_ID,
            context.getString(R.string.high_glucose_alert_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.high_glucose_alert_channel_description)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 350, 250, 350)
        }

        notificationManager.createNotificationChannel(lowChannel)
        notificationManager.createNotificationChannel(highChannel)
    }
}
