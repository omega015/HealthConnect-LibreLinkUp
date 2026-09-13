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

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class AlertSettingsAckListenerService : WearableListenerService() {
    companion object {
        private const val TAG = "LibreLinkUpAlert"
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != GlucoseAlertSettings.SETTINGS_ACK_PATH) continue

            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
            if (!dataMap.containsKey(GlucoseAlertSettings.KEY_REQUEST_ID)) continue

            val requestId = dataMap.getLong(GlucoseAlertSettings.KEY_REQUEST_ID)
            GlucoseAlertSettings(applicationContext).recordWatchAcknowledgement(requestId)
            Log.i(TAG, "Received Wear alert settings acknowledgement requestId=$requestId")
        }
    }
}
