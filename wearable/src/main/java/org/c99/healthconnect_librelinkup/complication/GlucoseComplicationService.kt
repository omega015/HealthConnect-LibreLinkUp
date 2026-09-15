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

import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import org.c99.healthconnect_librelinkup.DataLayerListenerService
import org.c99.healthconnect_librelinkup.GlucoseAlertManager
import org.c99.healthconnect_librelinkup.R
import java.util.Locale

class GlucoseComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val icon = Icon.createWithResource(this, R.drawable.water_drop)
        return when (type) {
            ComplicationType.SHORT_TEXT -> createShortTextData(
                icon = icon,
                value = "5.5",
                description = "5.5 mmol/L"
            )

            ComplicationType.RANGED_VALUE -> createRangedValueData(
                icon = icon,
                value = "5.5",
                description = "5.5 mmol/L",
                rangeValue = 5.5f,
                rangeMin = 2.2f,
                rangeMax = 22.2f
            )

            ComplicationType.LONG_TEXT -> createLongTextData(
                text = "5.5 mmol/L",
                description = "5.5 mmol/L"
            )

            else -> NoDataComplicationData()
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        try {
            val glucose = getSharedPreferences("glucose", MODE_PRIVATE)
            if (glucose.contains(DataLayerListenerService.GLUCOSE_KEY)) {
                val trendArrow = glucose.getInt(DataLayerListenerService.TREND_ARROW_KEY, -1)
                val icon = trendIcon(trendArrow)
                val sourceUnits = glucose.getInt(DataLayerListenerService.UNITS_KEY, 1)
                val sourceValue = glucose.getFloat(DataLayerListenerService.GLUCOSE_KEY, 0f)
                val glucoseMgDl = glucose.getFloat(
                    DataLayerListenerService.GLUCOSE_MGDL_KEY,
                    if (sourceUnits == 1) sourceValue else sourceValue * 18f
                )
                val displayUnits = getSharedPreferences(GlucoseAlertManager.PREFS_NAME, MODE_PRIVATE)
                    .getString(GlucoseAlertManager.KEY_DISPLAY_UNITS, GlucoseAlertManager.UNITS_MMOL)
                    ?: GlucoseAlertManager.UNITS_MMOL

                val useMgDl = displayUnits == GlucoseAlertManager.UNITS_MGDL
                val displayValue = if (useMgDl) {
                    String.format(Locale.US, "%.0f", glucoseMgDl)
                } else {
                    String.format(Locale.US, "%.1f", glucoseMgDl / 18f)
                }
                val unitsLabel = if (useMgDl) "mg/dL" else "mmol/L"
                val trend = trendSymbol(trendArrow)
                val description = listOf(displayValue, unitsLabel, trend)
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")

                return when (request.complicationType) {
                    ComplicationType.SHORT_TEXT -> createShortTextData(
                        icon = icon,
                        value = displayValue,
                        description = description
                    )

                    ComplicationType.RANGED_VALUE -> {
                        val rangeValue = if (useMgDl) glucoseMgDl else glucoseMgDl / 18f
                        val rangeMin = if (useMgDl) 40f else 40f / 18f
                        val rangeMax = if (useMgDl) 400f else 400f / 18f
                        createRangedValueData(
                            icon = icon,
                            value = displayValue,
                            description = description,
                            rangeValue = rangeValue,
                            rangeMin = rangeMin,
                            rangeMax = rangeMax
                        )
                    }

                    ComplicationType.LONG_TEXT -> {
                        val longText = listOf(displayValue, unitsLabel, trend)
                            .filter { it.isNotEmpty() }
                            .joinToString(" ")
                        createLongTextData(longText, description)
                    }

                    else -> NoDataComplicationData()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return NoDataComplicationData()
    }

    private fun trendIcon(trendArrow: Int): Icon = when (trendArrow) {
        1 -> Icon.createWithResource(this, R.drawable.arrow_down)
        2 -> Icon.createWithResource(this, R.drawable.arrow_down_right)
        3 -> Icon.createWithResource(this, R.drawable.arrow_right)
        4 -> Icon.createWithResource(this, R.drawable.arrow_up_right)
        5 -> Icon.createWithResource(this, R.drawable.arrow_up)
        else -> Icon.createWithResource(this, R.drawable.water_drop)
    }

    private fun trendSymbol(trendArrow: Int): String = when (trendArrow) {
        1 -> "↓"
        2 -> "↘"
        3 -> "→"
        4 -> "↗"
        5 -> "↑"
        else -> ""
    }

    private fun createShortTextData(
        icon: Icon,
        value: String,
        description: String
    ): ComplicationData = ShortTextComplicationData.Builder(
        PlainComplicationText.Builder(value).build(),
        PlainComplicationText.Builder(description).build()
    )
        .setMonochromaticImage(MonochromaticImage.Builder(icon).build())
        .build()

    private fun createRangedValueData(
        icon: Icon,
        value: String,
        description: String,
        rangeValue: Float,
        rangeMin: Float,
        rangeMax: Float
    ): ComplicationData = RangedValueComplicationData.Builder(
        value = rangeValue.coerceIn(rangeMin, rangeMax),
        min = rangeMin,
        max = rangeMax,
        contentDescription = PlainComplicationText.Builder(description).build()
    )
        .setText(PlainComplicationText.Builder(value).build())
        .setMonochromaticImage(MonochromaticImage.Builder(icon).build())
        .build()

    private fun createLongTextData(
        text: String,
        description: String
    ): ComplicationData = LongTextComplicationData.Builder(
        PlainComplicationText.Builder(text).build(),
        PlainComplicationText.Builder(description).build()
    ).build()
}
