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
 *
 * Alert settings are deliberately disabled by default. A later settings-sync step will populate
 * these preferences from the companion phone app. Keeping the alert state on the watch means a
 * reading is evaluated as soon as the Wear Data Layer delivers it.
 */
object GlucoseAlertManager {
    private const val TAG = "LibreLinkUpAlert"

    const val PREFS_NAME = "glucose_alert_settings"
    const val KEY_LOW_ENABLED = "low_enabled"
    const val KEY_HIGH_ENABLED = "high_enabled"
    const val KEY_LOW_THRESHOLD_MGDL = "low_threshold_mgdl"
    const val KEY_HIGH_THRESHOLD_MGDL = "high_threshold_mgdl"

    private const val KEY_ALERT_STATE = "alert_state"

    private const val STATE_NORMAL = "normal"
    private const val STATE_LOW = "low"
    private const val STATE_HIGH = "high"

    private const val DEFAULT_LOW_THRESHOLD_MGDL = 70f
    private const val DEFAULT_HIGH_THRESHOLD_MGDL = 180f

    // Require a small move back inside the target range before re-arming an alert. This avoids
    // repeated alarms when readings hover around the configured boundary.
    private const val HYSTERESIS_MGDL = 5f

    private const val LOW_CHANNEL_ID = "low_glucose_alerts"
    private const val HIGH_CHANNEL_ID = "high_glucose_alerts"
    private const val LOW_NOTIFICATION_ID = 2001
    private const val HIGH_NOTIFICATION_ID = 2002

    fun evaluate(context: Context, glucoseValue: Float, glucoseUnits: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lowEnabled = prefs.getBoolean(KEY_LOW_ENABLED, false)
        val highEnabled = prefs.getBoolean(KEY_HIGH_ENABLED, false)

        if (!lowEnabled && !highEnabled) {
            return
        }

        val glucoseMgDl = if (glucoseUnits == 1) glucoseValue else glucoseValue * 18f
        val lowThreshold = prefs.getFloat(KEY_LOW_THRESHOLD_MGDL, DEFAULT_LOW_THRESHOLD_MGDL)
        val highThreshold = prefs.getFloat(KEY_HIGH_THRESHOLD_MGDL, DEFAULT_HIGH_THRESHOLD_MGDL)
        val previousState = prefs.getString(KEY_ALERT_STATE, STATE_NORMAL) ?: STATE_NORMAL

        val nextState = when {
            lowEnabled && glucoseMgDl <= lowThreshold -> STATE_LOW
            highEnabled && glucoseMgDl >= highThreshold -> STATE_HIGH
            previousState == STATE_LOW && glucoseMgDl < lowThreshold + HYSTERESIS_MGDL -> STATE_LOW
            previousState == STATE_HIGH && glucoseMgDl > highThreshold - HYSTERESIS_MGDL -> STATE_HIGH
            else -> STATE_NORMAL
        }

        if (nextState != previousState) {
            prefs.edit().putString(KEY_ALERT_STATE, nextState).apply()

            when (nextState) {
                STATE_LOW -> showAlert(context, true, glucoseValue, glucoseUnits)
                STATE_HIGH -> showAlert(context, false, glucoseValue, glucoseUnits)
                STATE_NORMAL -> {
                    val notificationManager = context.getSystemService(NotificationManager::class.java)
                    notificationManager.cancel(LOW_NOTIFICATION_ID)
                    notificationManager.cancel(HIGH_NOTIFICATION_ID)
                    Log.i(TAG, "Glucose returned to alert re-arm range")
                }
            }
        }
    }

    private fun showAlert(context: Context, low: Boolean, glucoseValue: Float, glucoseUnits: Int) {
        createNotificationChannels(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Glucose alert suppressed because watch notification permission is not granted")
            return
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
    }

    private fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

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
