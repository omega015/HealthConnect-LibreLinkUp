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
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import org.c99.healthconnect_librelinkup.complication.GlucoseComplicationService
import org.c99.healthconnect_librelinkup.tile.GlucoseTileService

class DataLayerListenerService : WearableListenerService() {
    companion object {
        private const val TAG = "LibreLinkUpAlert"
        const val GLUCOSE_KEY: String = "org.c99.healthconnect_librelinkup.glucose"
        const val TREND_ARROW_KEY: String = "org.c99.healthconnect_librelinkup.trendArrow"
        const val COLOR_KEY: String = "org.c99.healthconnect_librelinkup.color"
        const val UNITS_KEY = "org.c99.healthconnect_librelinkup.units"
        const val TIMESTAMP_KEY = "org.c99.healthconnect_librelinkup.timestamp"
        private const val ALERT_SETTINGS_PATH = "/alert-settings"
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            val uri = event.dataItem.uri
            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap

            when (uri.path) {
                "/glucose" -> {
                    val glucoseValue = dataMap.getFloat(GLUCOSE_KEY)
                    val glucoseUnits = dataMap.getInt(UNITS_KEY)

                    val glucose =
                        applicationContext.getSharedPreferences("glucose", MODE_PRIVATE).edit()
                    glucose.putFloat(GLUCOSE_KEY, glucoseValue)
                    glucose.putInt(TREND_ARROW_KEY, dataMap.getInt(TREND_ARROW_KEY))
                    glucose.putInt(COLOR_KEY, dataMap.getInt(COLOR_KEY))
                    glucose.putInt(UNITS_KEY, glucoseUnits)
                    glucose.putString(TIMESTAMP_KEY, dataMap.getString(TIMESTAMP_KEY))
                    glucose.commit()

                    GlucoseAlertManager.evaluate(applicationContext, glucoseValue, glucoseUnits)

                    ComplicationDataSourceUpdateRequester.create(
                        applicationContext,
                        ComponentName(applicationContext, GlucoseComplicationService::class.java)
                    ).requestUpdateAll()
                    TileService.getUpdater(applicationContext).requestUpdate(GlucoseTileService::class.java)
                }

                ALERT_SETTINGS_PATH -> {
                    val alertPrefs = applicationContext.getSharedPreferences(
                        GlucoseAlertManager.PREFS_NAME,
                        MODE_PRIVATE
                    )
                    alertPrefs.edit()
                        .putBoolean(
                            GlucoseAlertManager.KEY_LOW_ENABLED,
                            dataMap.getBoolean(GlucoseAlertManager.KEY_LOW_ENABLED)
                        )
                        .putBoolean(
                            GlucoseAlertManager.KEY_HIGH_ENABLED,
                            dataMap.getBoolean(GlucoseAlertManager.KEY_HIGH_ENABLED)
                        )
                        .putFloat(
                            GlucoseAlertManager.KEY_LOW_THRESHOLD_MGDL,
                            dataMap.getFloat(GlucoseAlertManager.KEY_LOW_THRESHOLD_MGDL)
                        )
                        .putFloat(
                            GlucoseAlertManager.KEY_HIGH_THRESHOLD_MGDL,
                            dataMap.getFloat(GlucoseAlertManager.KEY_HIGH_THRESHOLD_MGDL)
                        )
                        .putBoolean(
                            GlucoseAlertManager.KEY_LOW_REPEAT_ENABLED,
                            dataMap.getBoolean(GlucoseAlertManager.KEY_LOW_REPEAT_ENABLED)
                        )
                        .putBoolean(
                            GlucoseAlertManager.KEY_HIGH_REPEAT_ENABLED,
                            dataMap.getBoolean(GlucoseAlertManager.KEY_HIGH_REPEAT_ENABLED)
                        )
                        .putInt(
                            GlucoseAlertManager.KEY_LOW_REPEAT_INTERVAL_MINUTES,
                            dataMap.getInt(GlucoseAlertManager.KEY_LOW_REPEAT_INTERVAL_MINUTES)
                        )
                        .putInt(
                            GlucoseAlertManager.KEY_HIGH_REPEAT_INTERVAL_MINUTES,
                            dataMap.getInt(GlucoseAlertManager.KEY_HIGH_REPEAT_INTERVAL_MINUTES)
                        )
                        .putFloat(
                            GlucoseAlertManager.KEY_LOW_HYSTERESIS_MGDL,
                            dataMap.getFloat(GlucoseAlertManager.KEY_LOW_HYSTERESIS_MGDL)
                        )
                        .putFloat(
                            GlucoseAlertManager.KEY_HIGH_HYSTERESIS_MGDL,
                            dataMap.getFloat(GlucoseAlertManager.KEY_HIGH_HYSTERESIS_MGDL)
                        )
                        .remove("alert_state")
                        .remove("last_low_alert_time_ms")
                        .remove("last_high_alert_time_ms")
                        .apply()

                    Log.i(
                        TAG,
                        "Wear alert settings updated: low=" +
                            dataMap.getBoolean(GlucoseAlertManager.KEY_LOW_ENABLED) +
                            " threshold=" +
                            dataMap.getFloat(GlucoseAlertManager.KEY_LOW_THRESHOLD_MGDL) +
                            "mg/dL repeat=" +
                            dataMap.getBoolean(GlucoseAlertManager.KEY_LOW_REPEAT_ENABLED) +
                            "/" + dataMap.getInt(GlucoseAlertManager.KEY_LOW_REPEAT_INTERVAL_MINUTES) +
                            "m hysteresis=" +
                            dataMap.getFloat(GlucoseAlertManager.KEY_LOW_HYSTERESIS_MGDL) +
                            "mg/dL high=" +
                            dataMap.getBoolean(GlucoseAlertManager.KEY_HIGH_ENABLED) +
                            " threshold=" +
                            dataMap.getFloat(GlucoseAlertManager.KEY_HIGH_THRESHOLD_MGDL) +
                            "mg/dL repeat=" +
                            dataMap.getBoolean(GlucoseAlertManager.KEY_HIGH_REPEAT_ENABLED) +
                            "/" + dataMap.getInt(GlucoseAlertManager.KEY_HIGH_REPEAT_INTERVAL_MINUTES) +
                            "m hysteresis=" +
                            dataMap.getFloat(GlucoseAlertManager.KEY_HIGH_HYSTERESIS_MGDL) +
                            "mg/dL"
                    )
                }
            }
        }
    }
}
