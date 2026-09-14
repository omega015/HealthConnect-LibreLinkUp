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

package org.c99.healthconnect_librelinkup.complication

import android.annotation.SuppressLint
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import org.c99.healthconnect_librelinkup.DataLayerListenerService
import org.c99.healthconnect_librelinkup.GlucoseAlertManager
import org.c99.healthconnect_librelinkup.R

class GlucoseComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) {
            return NoDataComplicationData()
        }
        return createComplicationData(Icon.createWithResource(this, R.drawable.water_drop), "99")
    }

    @SuppressLint("DefaultLocale")
override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
    try {
        val glucose = getSharedPreferences("glucose", MODE_PRIVATE)
        if (glucose.contains(DataLayerListenerService.GLUCOSE_KEY)) {
            val icon = when (glucose.getInt(DataLayerListenerService.TREND_ARROW_KEY, -1)) {
                1 -> Icon.createWithResource(this, R.drawable.arrow_down)
                2 -> Icon.createWithResource(this, R.drawable.arrow_down_right)
                3 -> Icon.createWithResource(this, R.drawable.arrow_right)
                4 -> Icon.createWithResource(this, R.drawable.arrow_up_right)
                5 -> Icon.createWithResource(this, R.drawable.arrow_up)
                else -> Icon.createWithResource(this, R.drawable.water_drop)
            }
            val sourceUnits = glucose.getInt(DataLayerListenerService.UNITS_KEY, 1)
            val sourceValue = glucose.getFloat(DataLayerListenerService.GLUCOSE_KEY, 0f)
            val glucoseMgDl = glucose.getFloat(
                DataLayerListenerService.GLUCOSE_MGDL_KEY,
                if (sourceUnits == 1) sourceValue else sourceValue * 18f
            )
            val displayUnits = getSharedPreferences(GlucoseAlertManager.PREFS_NAME, MODE_PRIVATE)
                .getString(GlucoseAlertManager.KEY_DISPLAY_UNITS, GlucoseAlertManager.UNITS_MMOL)
                ?: GlucoseAlertManager.UNITS_MMOL
            val value = if (displayUnits == GlucoseAlertManager.UNITS_MGDL) {
                String.format("%.0f", glucoseMgDl)
            } else {
                String.format("%.1f", glucoseMgDl / 18f)
            }
            return createComplicationData(icon, value)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return NoDataComplicationData()
}

    private fun createComplicationData(icon: Icon, glucose: String) =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(glucose).build(),
            contentDescription = PlainComplicationText.Builder(glucose).build()
        )
        .setMonochromaticImage(MonochromaticImage.Builder(image = icon).build())
        .build()
}