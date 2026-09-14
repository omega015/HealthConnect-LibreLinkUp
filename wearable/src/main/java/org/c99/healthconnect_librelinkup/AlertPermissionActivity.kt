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

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class AlertPermissionActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var enableButton: Button
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = (20 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(padding, padding, padding, padding)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val title = TextView(this).apply {
            text = getString(R.string.alert_permission_title)
            textSize = 20f
            gravity = Gravity.CENTER
        }
        val explanation = TextView(this).apply {
            text = getString(R.string.alert_permission_explanation)
            gravity = Gravity.CENTER
            setPadding(0, padding / 2, 0, padding / 2)
        }
        statusText = TextView(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, padding / 2)
        }
        enableButton = Button(this).apply {
            text = getString(R.string.alert_permission_enable)
            setOnClickListener { requestAlertPermission() }
        }

        content.addView(title)
        content.addView(explanation)
        content.addView(statusText)
        content.addView(enableButton)

        scrollView = ScrollView(this).apply {
            isFillViewport = true
            isFocusable = true
            isFocusableInTouchMode = true
            addView(content)
            setOnGenericMotionListener { _, event ->
                if (event.action == MotionEvent.ACTION_SCROLL) {
                    val delta = event.getAxisValue(MotionEvent.AXIS_SCROLL)
                    if (delta != 0f) {
                        smoothScrollBy(0, (-delta * 80 * resources.displayMetrics.density).toInt())
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
        }

        setContentView(scrollView)
        scrollView.requestFocus()
        refreshState()
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun requestAlertPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        } else {
            refreshState()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            refreshState()
        }
    }

    private fun refreshState() {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        statusText.text = getString(
            if (granted) R.string.alert_permission_enabled else R.string.alert_permission_denied
        )
        enableButton.visibility = if (granted) View.GONE else View.VISIBLE
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 100
    }
}
