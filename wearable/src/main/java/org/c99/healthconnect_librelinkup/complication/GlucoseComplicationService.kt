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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import org.c99.healthconnect_librelinkup.DataLayerListenerService
import org.c99.healthconnect_librelinkup.GlucoseAlertManager
import org.c99.healthconnect_librelinkup.R
import java.util.Locale

open class GlucoseComplicationService : SuspendingComplicationDataSourceService() {

    protected open val includeTrendInText: Boolean = false
    protected open val includeTrendInImage: Boolean = false
    protected open val useTrendIconInText: Boolean = true

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val icon = if (useTrendIconInText) {
            Icon.createWithResource(this, R.drawable.arrow_up_right)
        } else {
            Icon.createWithResource(this, R.drawable.water_drop)
        }
        val previewTrend = "↗"
        val previewTextValue = valueWithOptionalTrend("5.5", previewTrend, includeTrendInText)
        val previewImageValue = valueWithOptionalTrend("5.5", previewTrend, includeTrendInImage)
        val description = "5.5 mmol/L ↗"

        return when (type) {
            ComplicationType.SHORT_TEXT -> createShortTextData(
                icon = icon,
                value = previewTextValue,
                description = description
            )

            ComplicationType.RANGED_VALUE -> createRangedValueData(
                icon = icon,
                value = previewTextValue,
                description = description,
                rangeValue = 5.5f,
                rangeMin = 2.2f,
                rangeMax = 22.2f
            )

            ComplicationType.LONG_TEXT -> createLongTextData(
                text = if (includeTrendInText) "5.5 mmol/L ↗" else "5.5 mmol/L",
                description = description
            )

            ComplicationType.MONOCHROMATIC_IMAGE -> createMonochromaticImageData(
                value = previewImageValue,
                description = description
            )

            ComplicationType.SMALL_IMAGE -> createSmallImageData(
                value = previewImageValue,
                description = description
            )

            else -> NoDataComplicationData()
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        try {
            val glucose = getSharedPreferences("glucose", MODE_PRIVATE)
            if (glucose.contains(DataLayerListenerService.GLUCOSE_KEY)) {
                val trendArrow = glucose.getInt(DataLayerListenerService.TREND_ARROW_KEY, -1)
                val icon = if (useTrendIconInText) {
                    trendIcon(trendArrow)
                } else {
                    Icon.createWithResource(this, R.drawable.water_drop)
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

                val useMgDl = displayUnits == GlucoseAlertManager.UNITS_MGDL
                val displayValue = if (useMgDl) {
                    String.format(Locale.US, "%.0f", glucoseMgDl)
                } else {
                    String.format(Locale.US, "%.1f", glucoseMgDl / 18f)
                }
                val unitsLabel = if (useMgDl) "mg/dL" else "mmol/L"
                val trend = trendSymbol(trendArrow)
                val textValue = valueWithOptionalTrend(displayValue, trend, includeTrendInText)
                val imageValue = valueWithOptionalTrend(displayValue, trend, includeTrendInImage)
                val description = listOf(displayValue, unitsLabel, trend)
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")

                return when (request.complicationType) {
                    ComplicationType.SHORT_TEXT -> createShortTextData(
                        icon = icon,
                        value = textValue,
                        description = description
                    )

                    ComplicationType.RANGED_VALUE -> {
                        val rangeValue = if (useMgDl) glucoseMgDl else glucoseMgDl / 18f
                        val rangeMin = if (useMgDl) 40f else 40f / 18f
                        val rangeMax = if (useMgDl) 400f else 400f / 18f
                        createRangedValueData(
                            icon = icon,
                            value = textValue,
                            description = description,
                            rangeValue = rangeValue,
                            rangeMin = rangeMin,
                            rangeMax = rangeMax
                        )
                    }

                    ComplicationType.LONG_TEXT -> {
                        val longText = if (includeTrendInText) {
                            listOf(displayValue, unitsLabel, trend)
                                .filter { it.isNotEmpty() }
                                .joinToString(" ")
                        } else {
                            listOf(displayValue, unitsLabel)
                                .filter { it.isNotEmpty() }
                                .joinToString(" ")
                        }
                        createLongTextData(longText, description)
                    }

                    ComplicationType.MONOCHROMATIC_IMAGE -> createMonochromaticImageData(
                        value = imageValue,
                        description = description
                    )

                    ComplicationType.SMALL_IMAGE -> createSmallImageData(
                        value = imageValue,
                        description = description
                    )

                    else -> NoDataComplicationData()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return NoDataComplicationData()
    }

    private fun valueWithOptionalTrend(value: String, trend: String, includeTrend: Boolean): String =
        if (includeTrend && trend.isNotEmpty()) "$value $trend" else value

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

    private fun createMonochromaticImageData(
        value: String,
        description: String
    ): ComplicationData {
        val icon = createGlucoseImageIcon(value)
        val image = MonochromaticImage.Builder(icon).build()
        return MonochromaticImageComplicationData.Builder(
            image,
            PlainComplicationText.Builder(description).build()
        ).build()
    }

    private fun createSmallImageData(
        value: String,
        description: String
    ): ComplicationData {
        val icon = createGlucoseImageIcon(value)
        val image = SmallImage.Builder(
            icon,
            SmallImageType.PHOTO
        ).build()
        return SmallImageComplicationData.Builder(
            image,
            PlainComplicationText.Builder(description).build()
        ).build()
    }

    private fun createGlucoseImageIcon(value: String): Icon {
        val size = 300
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = size * 0.52f
        }

        val maxWidth = size * 0.82f
        val measuredWidth = paint.measureText(value)
        if (measuredWidth > maxWidth && measuredWidth > 0f) {
            paint.textSize *= maxWidth / measuredWidth
        }

        val baseline = size / 2f - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(value, size / 2f, baseline, paint)
        return Icon.createWithBitmap(bitmap)
    }
}
