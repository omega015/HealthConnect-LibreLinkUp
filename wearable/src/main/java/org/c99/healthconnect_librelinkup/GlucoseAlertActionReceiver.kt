/*
 * Copyright (c) 2026 omega015
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class GlucoseAlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ACK_LOW -> GlucoseAlertManager.acknowledge(context, low = true)
            ACTION_ACK_HIGH -> GlucoseAlertManager.acknowledge(context, low = false)
            ACTION_DISMISS_LOW -> GlucoseAlertManager.dismiss(context, low = true)
            ACTION_DISMISS_HIGH -> GlucoseAlertManager.dismiss(context, low = false)
        }
    }

    companion object {
        const val ACTION_ACK_LOW = "org.c99.healthconnect_librelinkup.ACK_LOW_ALERT"
        const val ACTION_ACK_HIGH = "org.c99.healthconnect_librelinkup.ACK_HIGH_ALERT"
        const val ACTION_DISMISS_LOW = "org.c99.healthconnect_librelinkup.DISMISS_LOW_ALERT"
        const val ACTION_DISMISS_HIGH = "org.c99.healthconnect_librelinkup.DISMISS_HIGH_ALERT"
    }
}
