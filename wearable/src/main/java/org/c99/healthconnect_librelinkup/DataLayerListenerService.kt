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

import android.content.ComponentName
import android.util.Log
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import org.c99.healthconnect_librelinkup.complication.GlucoseComplicationService
import org.c99.healthconnect_librelinkup.tile.GlucoseTileService

class DataLayerListenerService : WearableListenerService() {
    companion object {
        private const val TAG = "LibreLinkUpAlert"
        const val GLUCOSE_KEY: String = "org.c99.healthconnect_librelinkup.glucose"
    const val GLUCOSE_MGDL_KEY: String = "org.c99.healthconnect_librelinkup.glucoseMgDl"
        const val TREND_ARROW_KEY: String = "org.c99.healthconnect_librelinkup.trendArrow"
        const val COLOR_KEY: String = "org.c99.healthconnect_librelinkup.color"
        const val UNITS_KEY = "org.c99.healthconnect_librelinkup.units"
        const val TIMESTAMP_KEY = "org.c99.healthconnect_librelinkup.timestamp"
        private const val ALERT_SETTINGS_PATH = "/alert-settings"
        private const val ALERT_SETTINGS_ACK_PATH = "/alert-settings-ack"
        private const val KEY_REQUEST_ID = "request_id"
        private const val KEY_ACK_AT = "ack_at"
        private const val KEY_ALERT_STATE = "alert_state"
        private const val KEY_LAST_LOW_ALERT_TIME_MS = "last_low_alert_time_ms"
        private const val KEY_LAST_HIGH_ALERT_TIME_MS = "last_high_alert_time_ms"
        private const val STATE_LOW = "low"
        private const val STATE_HIGH = "high"
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            val uri = event.dataItem.uri
            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap

            when (uri.path) {
                "/glucose" -> {
            val glucoseValue = dataMap.getFloat(GLUCOSE_KEY)
            val glucoseUnits = dataMap.getInt(UNITS_KEY)
            val glucoseMgDl = if (dataMap.containsKey(GLUCOSE_MGDL_KEY)) {
                dataMap.getFloat(GLUCOSE_MGDL_KEY)
            } else if (glucoseUnits == 1) {
                glucoseValue
            } else {
                glucoseValue * 18f
            }

            val glucose = applicationContext.getSharedPreferences("glucose", MODE_PRIVATE).edit()
            glucose.putFloat(GLUCOSE_KEY, glucoseValue)
            glucose.putFloat(GLUCOSE_MGDL_KEY, glucoseMgDl)
            glucose.putInt(TREND_ARROW_KEY, dataMap.getInt(TREND_ARROW_KEY))
            glucose.putInt(COLOR_KEY, dataMap.getInt(COLOR_KEY))
            glucose.putInt(UNITS_KEY, glucoseUnits)
            glucose.putString(TIMESTAMP_KEY, dataMap.getString(TIMESTAMP_KEY))
            glucose.commit()

            GlucoseAlertManager.evaluate(applicationContext, glucoseMgDl)
            ComplicationDataSourceUpdateRequester.create(
                applicationContext,
                ComponentName(applicationContext, GlucoseComplicationService::class.java)
            ).requestUpdateAll()
            TileService.getUpdater(applicationContext).requestUpdate(GlucoseTileService::class.java)
        }

                ALERT_SETTINGS_PATH -> updateAlertSettings(dataMap)
            }
        }
    }

    private fun updateAlertSettings(dataMap: DataMap) {
    val alertPrefs = applicationContext.getSharedPreferences(GlucoseAlertManager.PREFS_NAME, MODE_PRIVATE)
    val oldLowEnabled = alertPrefs.getBoolean(GlucoseAlertManager.KEY_LOW_ENABLED, false)
    val oldHighEnabled = alertPrefs.getBoolean(GlucoseAlertManager.KEY_HIGH_ENABLED, false)
    val oldLowThreshold = alertPrefs.getFloat(GlucoseAlertManager.KEY_LOW_THRESHOLD_MGDL, 70f)
    val oldHighThreshold = alertPrefs.getFloat(GlucoseAlertManager.KEY_HIGH_THRESHOLD_MGDL, 180f)
    val oldLowHysteresis = alertPrefs.getFloat(GlucoseAlertManager.KEY_LOW_HYSTERESIS_MGDL, 5f)
    val oldHighHysteresis = alertPrefs.getFloat(GlucoseAlertManager.KEY_HIGH_HYSTERESIS_MGDL, 5f)
    val oldDisplayUnits = alertPrefs.getString(GlucoseAlertManager.KEY_DISPLAY_UNITS, GlucoseAlertManager.UNITS_MMOL)
        ?: GlucoseAlertManager.UNITS_MMOL
    val previousState = alertPrefs.getString(KEY_ALERT_STATE, null)

    val lowEnabled = booleanValue(dataMap, GlucoseAlertManager.KEY_LOW_ENABLED, oldLowEnabled)
    val highEnabled = booleanValue(dataMap, GlucoseAlertManager.KEY_HIGH_ENABLED, oldHighEnabled)
    val lowThreshold = floatValue(dataMap, GlucoseAlertManager.KEY_LOW_THRESHOLD_MGDL, oldLowThreshold)
        .coerceIn(GlucoseAlertManager.MIN_LOW_THRESHOLD_MGDL, GlucoseAlertManager.MAX_LOW_THRESHOLD_MGDL)
    val highThreshold = floatValue(dataMap, GlucoseAlertManager.KEY_HIGH_THRESHOLD_MGDL, oldHighThreshold)
        .coerceIn(GlucoseAlertManager.MIN_HIGH_THRESHOLD_MGDL, GlucoseAlertManager.MAX_HIGH_THRESHOLD_MGDL)
    val lowPersistentVibration = booleanValue(dataMap, GlucoseAlertManager.KEY_LOW_PERSISTENT_VIBRATION,
        alertPrefs.getBoolean(GlucoseAlertManager.KEY_LOW_PERSISTENT_VIBRATION, false))
    val highPersistentVibration = booleanValue(dataMap, GlucoseAlertManager.KEY_HIGH_PERSISTENT_VIBRATION,
        alertPrefs.getBoolean(GlucoseAlertManager.KEY_HIGH_PERSISTENT_VIBRATION, false))
    val lowRepeatEnabled = booleanValue(dataMap, GlucoseAlertManager.KEY_LOW_REPEAT_ENABLED,
        alertPrefs.getBoolean(GlucoseAlertManager.KEY_LOW_REPEAT_ENABLED, false))
    val highRepeatEnabled = booleanValue(dataMap, GlucoseAlertManager.KEY_HIGH_REPEAT_ENABLED,
        alertPrefs.getBoolean(GlucoseAlertManager.KEY_HIGH_REPEAT_ENABLED, false))
    val lowRepeatInterval = intValue(dataMap, GlucoseAlertManager.KEY_LOW_REPEAT_INTERVAL_MINUTES,
        alertPrefs.getInt(GlucoseAlertManager.KEY_LOW_REPEAT_INTERVAL_MINUTES, 15))
    val highRepeatInterval = intValue(dataMap, GlucoseAlertManager.KEY_HIGH_REPEAT_INTERVAL_MINUTES,
        alertPrefs.getInt(GlucoseAlertManager.KEY_HIGH_REPEAT_INTERVAL_MINUTES, 15))
    val lowHysteresis = floatValue(dataMap, GlucoseAlertManager.KEY_LOW_HYSTERESIS_MGDL, oldLowHysteresis)
        .coerceIn(0f, GlucoseAlertManager.MAX_HYSTERESIS_MGDL)
    val highHysteresis = floatValue(dataMap, GlucoseAlertManager.KEY_HIGH_HYSTERESIS_MGDL, oldHighHysteresis)
        .coerceIn(0f, GlucoseAlertManager.MAX_HYSTERESIS_MGDL)
    val displayUnits = stringValue(dataMap, GlucoseAlertManager.KEY_DISPLAY_UNITS, oldDisplayUnits).let {
        if (it == GlucoseAlertManager.UNITS_MGDL) GlucoseAlertManager.UNITS_MGDL else GlucoseAlertManager.UNITS_MMOL
    }

    val lowDefinitionChanged = oldLowEnabled != lowEnabled || oldLowThreshold != lowThreshold || oldLowHysteresis != lowHysteresis
    val highDefinitionChanged = oldHighEnabled != highEnabled || oldHighThreshold != highThreshold || oldHighHysteresis != highHysteresis
    val resetActiveState = (previousState == STATE_LOW && lowDefinitionChanged) ||
        (previousState == STATE_HIGH && highDefinitionChanged)

    val editor = alertPrefs.edit()
        .putBoolean(GlucoseAlertManager.KEY_LOW_ENABLED, lowEnabled)
        .putBoolean(GlucoseAlertManager.KEY_HIGH_ENABLED, highEnabled)
        .putFloat(GlucoseAlertManager.KEY_LOW_THRESHOLD_MGDL, lowThreshold)
        .putFloat(GlucoseAlertManager.KEY_HIGH_THRESHOLD_MGDL, highThreshold)
        .putBoolean(GlucoseAlertManager.KEY_LOW_PERSISTENT_VIBRATION, lowPersistentVibration)
        .putBoolean(GlucoseAlertManager.KEY_HIGH_PERSISTENT_VIBRATION, highPersistentVibration)
        .putBoolean(GlucoseAlertManager.KEY_LOW_REPEAT_ENABLED, lowRepeatEnabled)
        .putBoolean(GlucoseAlertManager.KEY_HIGH_REPEAT_ENABLED, highRepeatEnabled)
        .putInt(GlucoseAlertManager.KEY_LOW_REPEAT_INTERVAL_MINUTES, lowRepeatInterval)
        .putInt(GlucoseAlertManager.KEY_HIGH_REPEAT_INTERVAL_MINUTES, highRepeatInterval)
        .putFloat(GlucoseAlertManager.KEY_LOW_HYSTERESIS_MGDL, lowHysteresis)
        .putFloat(GlucoseAlertManager.KEY_HIGH_HYSTERESIS_MGDL, highHysteresis)
        .putString(GlucoseAlertManager.KEY_DISPLAY_UNITS, displayUnits)

    if (resetActiveState) {
        editor.remove(KEY_ALERT_STATE)
        if (previousState == STATE_LOW) editor.remove(KEY_LAST_LOW_ALERT_TIME_MS)
        if (previousState == STATE_HIGH) editor.remove(KEY_LAST_HIGH_ALERT_TIME_MS)
    }
    if (!editor.commit()) {
        Log.e(TAG, "Failed to persist Wear alert settings; acknowledgement not sent")
        return
    }

    if (previousState == STATE_LOW && !lowEnabled) GlucoseAlertManager.acknowledge(applicationContext, low = true)
    if (previousState == STATE_HIGH && !highEnabled) GlucoseAlertManager.acknowledge(applicationContext, low = false)

    if (oldDisplayUnits != displayUnits) {
        ComplicationDataSourceUpdateRequester.create(
            applicationContext,
            ComponentName(applicationContext, GlucoseComplicationService::class.java)
        ).requestUpdateAll()
        TileService.getUpdater(applicationContext).requestUpdate(GlucoseTileService::class.java)
    }

    Log.i(TAG, "Wear alert settings updated: displayUnits=$displayUnits low=$lowEnabled threshold=${lowThreshold}mg/dL high=$highEnabled threshold=${highThreshold}mg/dL")
    if (dataMap.containsKey(KEY_REQUEST_ID)) sendSettingsAcknowledgement(dataMap.getLong(KEY_REQUEST_ID))
}

    private fun sendSettingsAcknowledgement(requestId: Long) {
        if (requestId <= 0L) return

        val request = PutDataMapRequest.create(ALERT_SETTINGS_ACK_PATH).apply {
            dataMap.putLong(KEY_REQUEST_ID, requestId)
            dataMap.putLong(KEY_ACK_AT, System.currentTimeMillis())
        }

        Wearable.getDataClient(applicationContext)
            .putDataItem(request.asPutDataRequest().setUrgent())
            .addOnSuccessListener {
                Log.i(TAG, "Wear alert settings acknowledgement queued requestId=$requestId")
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to queue Wear alert settings acknowledgement requestId=$requestId", error)
            }
    }

    private fun booleanValue(dataMap: DataMap, key: String, fallback: Boolean): Boolean =
        if (dataMap.containsKey(key)) dataMap.getBoolean(key) else fallback

    private fun intValue(dataMap: DataMap, key: String, fallback: Int): Int =
        if (dataMap.containsKey(key)) dataMap.getInt(key) else fallback

    private fun stringValue(dataMap: DataMap, key: String, fallback: String): String =
    if (dataMap.containsKey(key)) dataMap.getString(key) ?: fallback else fallback

    private fun floatValue(dataMap: DataMap, key: String, fallback: Float): Float =
        if (dataMap.containsKey(key)) dataMap.getFloat(key) else fallback
}
