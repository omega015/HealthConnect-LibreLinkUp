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
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.c99.healthconnect_librelinkup.ui.theme.HealthConnectLibreLinkUpTheme
import java.util.Locale

data class LoginUiState(
    var url: String = "",
    var email: String = "",
    var password: String = "",
    var status: String = "",
    var isLoggedIn: Boolean = false,
    var version: String = "Version",
    var isIgnoringBatteryOptimizations: Boolean = false,
    var syncMode: String = LibreLinkUp.SYNC_MODE_STANDARD,
    var fastSyncIntervalMinutes: Int = LibreLinkUp.DEFAULT_FAST_SYNC_INTERVAL_MINUTES,
    var lowAlertEnabled: Boolean = false,
    var highAlertEnabled: Boolean = false,
    var lowAlertThreshold: String = "3.9",
    var highAlertThreshold: String = "10.0",
    var lowPersistentVibration: Boolean = false,
    var highPersistentVibration: Boolean = false,
    var lowRepeatEnabled: Boolean = false,
    var highRepeatEnabled: Boolean = false,
    var lowRepeatIntervalMinutes: Int = GlucoseAlertSettings.DEFAULT_REPEAT_INTERVAL_MINUTES,
    var highRepeatIntervalMinutes: Int = GlucoseAlertSettings.DEFAULT_REPEAT_INTERVAL_MINUTES,
    var lowHysteresis: String = "0.3",
    var highHysteresis: String = "0.3",
    var alertUnits: String = GlucoseAlertSettings.UNITS_MMOL
)

class LoginViewModel: ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun setUrl(url: String) { _uiState.value = _uiState.value.copy(url = url) }
    fun setEmail(email: String) { _uiState.value = _uiState.value.copy(email = email) }
    fun setPassword(password: String) { _uiState.value = _uiState.value.copy(password = password) }
    fun setStatus(status: String) { _uiState.value = _uiState.value.copy(status = status) }
    fun setIsLoggedIn(isLoggedIn: Boolean) { _uiState.value = _uiState.value.copy(isLoggedIn = isLoggedIn) }
    fun setVersion(version: String) { _uiState.value = _uiState.value.copy(version = version) }
    fun setIsIgnoringBatteryOptimizations(value: Boolean) {
        _uiState.value = _uiState.value.copy(isIgnoringBatteryOptimizations = value)
    }
    fun setSyncMode(syncMode: String) { _uiState.value = _uiState.value.copy(syncMode = syncMode) }
    fun setFastSyncIntervalMinutes(minutes: Int) {
        _uiState.value = _uiState.value.copy(fastSyncIntervalMinutes = minutes)
    }
    fun setLowAlertEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(lowAlertEnabled = enabled)
    }
    fun setHighAlertEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(highAlertEnabled = enabled)
    }
    fun setLowAlertThreshold(value: String) {
        _uiState.value = _uiState.value.copy(lowAlertThreshold = value)
    }
    fun setHighAlertThreshold(value: String) {
        _uiState.value = _uiState.value.copy(highAlertThreshold = value)
    }
    fun setLowPersistentVibration(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(lowPersistentVibration = enabled)
    }
    fun setHighPersistentVibration(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(highPersistentVibration = enabled)
    }
    fun setLowRepeatEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(lowRepeatEnabled = enabled)
    }
    fun setHighRepeatEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(highRepeatEnabled = enabled)
    }
    fun setLowRepeatIntervalMinutes(minutes: Int) {
        _uiState.value = _uiState.value.copy(lowRepeatIntervalMinutes = minutes)
    }
    fun setHighRepeatIntervalMinutes(minutes: Int) {
        _uiState.value = _uiState.value.copy(highRepeatIntervalMinutes = minutes)
    }
    fun setLowHysteresis(value: String) {
        _uiState.value = _uiState.value.copy(lowHysteresis = value)
    }
    fun setHighHysteresis(value: String) {
        _uiState.value = _uiState.value.copy(highHysteresis = value)
    }
    fun setAlertUnits(units: String) { _uiState.value = _uiState.value.copy(alertUnits = units) }
}

class MainActivity : ComponentActivity() {
    private lateinit var libreLinkUp: LibreLinkUp
    private lateinit var alertSettings: GlucoseAlertSettings
    private val viewModel: LoginViewModel by viewModels()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            applySyncMode(LibreLinkUp.SYNC_MODE_FAST)
            if (!granted) {
                Toast.makeText(
                    this,
                    "Fast sync is enabled without notification permission. Android may still show the service under Active apps.",
                    Toast.LENGTH_LONG
                ).show()
                Log.i(
                    "LibreLinkUp",
                    "Fast sync enabled without notification permission; foreground service notification hidden from notification drawer"
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        libreLinkUp = LibreLinkUp(this)
        alertSettings = GlucoseAlertSettings(this)

        enableEdgeToEdge()
        setContent {
            MainView(
                onUrlChanged = { libreLinkUp.setUrl(it) },
                onLoginButtonClicked = { onLoginButtonClicked() },
                onLogoutButtonClicked = { onLogoutButtonClicked() },
                onDisableBatteryRestrictionsButtonClicked = { onDisableBatteryRestrictionsButtonClicked() },
                onSyncModeChanged = { onSyncModeChanged(it) },
                onFastSyncIntervalChanged = { onFastSyncIntervalChanged(it) },
                onAlertUnitsChanged = { onAlertUnitsChanged(it) },
                onSaveAlertSettings = { onSaveAlertSettings() }
            )
        }

        viewModel.setUrl(libreLinkUp.url)
        viewModel.setSyncMode(libreLinkUp.syncMode)
        viewModel.setFastSyncIntervalMinutes(libreLinkUp.fastSyncIntervalMinutes)
        loadAlertSettings()
        alertSettings.sendToWear()

        val user = libreLinkUp.user
        val authTicket = libreLinkUp.authTicket
        if (
            user != null &&
            user.email != null &&
            authTicket != null &&
            !authTicket.token.isNullOrBlank()
        ) {
            viewModel.setEmail(user.email)
            viewModel.setIsLoggedIn(true)
            viewModel.setStatus("Logged in as " + user.firstName + " " + user.lastName)
        }

        val availabilityStatus = HealthConnectClient.getSdkStatus(this)
        if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE) {
            Toast.makeText(this, "HealthConnect is unavailable", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setPackage("com.android.vending")
                        data = Uri.parse("market://details?id=com.google.android.apps.healthdata")
                        putExtra("overlay", true)
                        putExtra("callerId", packageName)
                    }
                )
            } catch (e: ActivityNotFoundException) {
                startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                    }
                )
            }
            Toast.makeText(this, "HealthConnect not installed or requires an update", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        checkPermissions()

        viewModel.setVersion("Version " + packageManager.getPackageInfo(packageName, 0).versionName)
    }

    private fun loadAlertSettings() {
        val units = alertSettings.displayUnits
        viewModel.setAlertUnits(units)
        viewModel.setLowAlertEnabled(alertSettings.isLowEnabled)
        viewModel.setHighAlertEnabled(alertSettings.isHighEnabled)
        viewModel.setLowAlertThreshold(formatValue(alertSettings.lowThresholdMgDl, units))
        viewModel.setHighAlertThreshold(formatValue(alertSettings.highThresholdMgDl, units))
        viewModel.setLowPersistentVibration(alertSettings.isLowPersistentVibrationEnabled)
        viewModel.setHighPersistentVibration(alertSettings.isHighPersistentVibrationEnabled)
        viewModel.setLowRepeatEnabled(alertSettings.isLowRepeatEnabled)
        viewModel.setHighRepeatEnabled(alertSettings.isHighRepeatEnabled)
        viewModel.setLowRepeatIntervalMinutes(alertSettings.lowRepeatIntervalMinutes)
        viewModel.setHighRepeatIntervalMinutes(alertSettings.highRepeatIntervalMinutes)
        viewModel.setLowHysteresis(formatValue(alertSettings.lowHysteresisMgDl, units))
        viewModel.setHighHysteresis(formatValue(alertSettings.highHysteresisMgDl, units))
    }

    private fun formatValue(mgDl: Float, units: String): String {
        return if (units == GlucoseAlertSettings.UNITS_MGDL) {
            String.format(Locale.US, "%.0f", mgDl)
        } else {
            String.format(Locale.US, "%.1f", mgDl / 18f)
        }
    }

    private fun valueToMgDl(value: String, units: String, allowZero: Boolean = false): Float? {
        val parsed = value.trim().replace(',', '.').toFloatOrNull() ?: return null
        if ((!allowZero && parsed <= 0f) || (allowZero && parsed < 0f)) return null
        return if (units == GlucoseAlertSettings.UNITS_MGDL) parsed else parsed * 18f
    }

    private fun onAlertUnitsChanged(newUnits: String) {
        val state = viewModel.uiState.value
        if (state.alertUnits == newUnits) return

        val lowMgDl = valueToMgDl(state.lowAlertThreshold, state.alertUnits)
        val highMgDl = valueToMgDl(state.highAlertThreshold, state.alertUnits)
        val lowHysteresisMgDl = valueToMgDl(state.lowHysteresis, state.alertUnits, true)
        val highHysteresisMgDl = valueToMgDl(state.highHysteresis, state.alertUnits, true)
        viewModel.setAlertUnits(newUnits)
        if (lowMgDl != null) viewModel.setLowAlertThreshold(formatValue(lowMgDl, newUnits))
        if (highMgDl != null) viewModel.setHighAlertThreshold(formatValue(highMgDl, newUnits))
        if (lowHysteresisMgDl != null) viewModel.setLowHysteresis(formatValue(lowHysteresisMgDl, newUnits))
        if (highHysteresisMgDl != null) viewModel.setHighHysteresis(formatValue(highHysteresisMgDl, newUnits))
    }

    private fun onSaveAlertSettings() {
    val state = viewModel.uiState.value
    val lowMgDl = valueToMgDl(state.lowAlertThreshold, state.alertUnits)
    val highMgDl = valueToMgDl(state.highAlertThreshold, state.alertUnits)
    val lowHysteresisMgDl = valueToMgDl(state.lowHysteresis, state.alertUnits, true)
    val highHysteresisMgDl = valueToMgDl(state.highHysteresis, state.alertUnits, true)
    val lowDisplay = state.lowAlertThreshold.trim().replace(',', '.').toFloatOrNull()
    val highDisplay = state.highAlertThreshold.trim().replace(',', '.').toFloatOrNull()
    val lowHysteresisDisplay = state.lowHysteresis.trim().replace(',', '.').toFloatOrNull()
    val highHysteresisDisplay = state.highHysteresis.trim().replace(',', '.').toFloatOrNull()

    if (
        lowMgDl == null || highMgDl == null ||
        lowHysteresisMgDl == null || highHysteresisMgDl == null ||
        lowDisplay == null || highDisplay == null ||
        lowHysteresisDisplay == null || highHysteresisDisplay == null
    ) {
        Toast.makeText(this, getString(R.string.alert_settings_invalid), Toast.LENGTH_LONG).show()
        return
    }

    val thresholdsValid = if (state.alertUnits == GlucoseAlertSettings.UNITS_MGDL) {
        lowDisplay in 60f..100f && highDisplay in 120f..400f
    } else {
        lowDisplay in 3.3f..5.6f && highDisplay in 6.7f..22.2f
    }
    if (!thresholdsValid) {
        val message = if (state.alertUnits == GlucoseAlertSettings.UNITS_MGDL) {
            R.string.alert_settings_threshold_range_mgdl
        } else {
            R.string.alert_settings_threshold_range_mmol
        }
        Toast.makeText(this, getString(message), Toast.LENGTH_LONG).show()
        return
    }

    val maxHysteresis = if (state.alertUnits == GlucoseAlertSettings.UNITS_MGDL) 9f else 0.5f
    if (lowHysteresisDisplay !in 0f..maxHysteresis || highHysteresisDisplay !in 0f..maxHysteresis) {
        val message = if (state.alertUnits == GlucoseAlertSettings.UNITS_MGDL) {
            R.string.alert_settings_hysteresis_range_mgdl
        } else {
            R.string.alert_settings_hysteresis_range_mmol
        }
        Toast.makeText(this, getString(message), Toast.LENGTH_LONG).show()
        return
    }

    if (lowMgDl + lowHysteresisMgDl >= highMgDl - highHysteresisMgDl) {
        Toast.makeText(this, getString(R.string.alert_settings_rearm_overlap), Toast.LENGTH_LONG).show()
        return
    }

    alertSettings.saveAndSend(
        state.lowAlertEnabled,
        lowMgDl,
        state.lowPersistentVibration,
        state.lowRepeatEnabled,
        state.lowRepeatIntervalMinutes,
        lowHysteresisMgDl,
        state.highAlertEnabled,
        highMgDl,
        state.highPersistentVibration,
        state.highRepeatEnabled,
        state.highRepeatIntervalMinutes,
        highHysteresisMgDl,
        state.alertUnits
    )
    Toast.makeText(this, getString(R.string.alert_settings_saved), Toast.LENGTH_SHORT).show()
}

    private fun checkPermissions() {
        val permissions = setOf(
            HealthPermission.getReadPermission(BloodGlucoseRecord::class),
            HealthPermission.getWritePermission(BloodGlucoseRecord::class),
        )

        val requestPermissions = registerForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { granted ->
            if (granted.containsAll(permissions)) libreLinkUp.schedule()
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val granted = HealthConnectClient.getOrCreate(this@MainActivity)
                    .permissionController.getGrantedPermissions()
                if (granted.containsAll(permissions)) {
                    libreLinkUp.schedule()
                } else {
                    requestPermissions.launch(permissions)
                }
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        viewModel.setIsIgnoringBatteryOptimizations(
            powerManager.isIgnoringBatteryOptimizations(packageName)
        )
    }

    @SuppressLint("BatteryLife")
    private fun onDisableBatteryRestrictionsButtonClicked() {
        val intent = Intent()
        intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)
    }

    private fun onSyncModeChanged(mode: String) {
        if (
            mode == LibreLinkUp.SYNC_MODE_FAST &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        applySyncMode(mode)
    }

    private fun applySyncMode(mode: String) {
        libreLinkUp.syncMode = mode
        viewModel.setSyncMode(libreLinkUp.syncMode)
        libreLinkUp.applySyncSettings()
    }

    private fun onFastSyncIntervalChanged(minutes: Int) {
        libreLinkUp.fastSyncIntervalMinutes = minutes
        viewModel.setFastSyncIntervalMinutes(libreLinkUp.fastSyncIntervalMinutes)
        if (LibreLinkUp.SYNC_MODE_FAST == libreLinkUp.syncMode) {
            libreLinkUp.applySyncSettings()
        }
    }

    private fun onLogoutButtonClicked() {
        libreLinkUp.stopAllSync()
        libreLinkUp.authTicket = null
        libreLinkUp.user = null
        getSharedPreferences("glucose_sync_state", MODE_PRIVATE).edit().clear().apply()

        viewModel.setPassword("")
        viewModel.setIsLoggedIn(false)
        viewModel.setStatus("Logged out")
        Log.i("LibreLinkUp", "Logged out; cached session cleared and glucose sync stopped")
    }

    private fun onLoginButtonClicked() {
        if (viewModel.uiState.value.email.isNotBlank() && viewModel.uiState.value.password.isNotBlank()) {
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val loginResult = libreLinkUp.login(
                        viewModel.uiState.value.email,
                        viewModel.uiState.value.password
                    )
                    val loginData = loginResult?.data
                    val loginUser = loginData?.user
                    val loginTicket = loginData?.authTicket

                    if (
                        loginResult != null &&
                        loginResult.status == 0 &&
                        loginUser != null &&
                        loginTicket != null &&
                        !loginTicket.token.isNullOrBlank()
                    ) {
                        libreLinkUp.authTicket = loginTicket
                        libreLinkUp.user = loginUser
                        viewModel.setUrl(libreLinkUp.url)
                        viewModel.setPassword("")
                        viewModel.setIsLoggedIn(true)
                        CoroutineScope(Dispatchers.Main).launch { libreLinkUp.schedule() }
                        viewModel.setStatus("Logged in as " + loginUser.firstName + " " + loginUser.lastName)
                    } else {
                        if (loginResult != null && loginResult.error != null) {
                            Log.e("Libre", "Message: " + loginResult.error.message)
                        } else {
                            Log.e("Libre", "Login response did not contain a valid user and auth ticket")
                        }
                        viewModel.setStatus("Login failed. Check your username, password, and server.")
                    }
                } catch (e: Exception) {
                    Log.e("Libre", "Login failed", e)
                    viewModel.setUrl(libreLinkUp.url)
                    viewModel.setStatus("Login failed. Check your connection and server selection.")
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.autofill(
    autofillTypes: List<AutofillType>,
    onFill: ((String) -> Unit),
) = composed {
    val autofill = LocalAutofill.current
    val autofillNode = AutofillNode(onFill = onFill, autofillTypes = autofillTypes)
    LocalAutofillTree.current += autofillNode

    this.onGloballyPositioned {
        autofillNode.boundingBox = it.boundsInWindow()
    }.onFocusChanged { focusState ->
        autofill?.run {
            if (focusState.isFocused) requestAutofillForNode(autofillNode)
            else cancelAutofillForNode(autofillNode)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MainView(
    viewModel: LoginViewModel = viewModel(),
    onUrlChanged: (String) -> Unit = {},
    onLoginButtonClicked: () -> Unit = {},
    onLogoutButtonClicked: () -> Unit = {},
    onDisableBatteryRestrictionsButtonClicked: () -> Unit = {},
    onSyncModeChanged: (String) -> Unit = {},
    onFastSyncIntervalChanged: (Int) -> Unit = {},
    onAlertUnitsChanged: (String) -> Unit = {},
    onSaveAlertSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val apiEndpoints = stringArrayResource(id = R.array.api_endpoints)
    var serverExpanded by remember { mutableStateOf(false) }
    var syncModeExpanded by remember { mutableStateOf(false) }
    var intervalExpanded by remember { mutableStateOf(false) }
    var alertUnitsExpanded by remember { mutableStateOf(false) }
    var lowRepeatExpanded by remember { mutableStateOf(false) }
    var highRepeatExpanded by remember { mutableStateOf(false) }

    HealthConnectLibreLinkUpTheme {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    colors = topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = { Text(stringResource(id = R.string.title_activity_main)) }
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        start = 16.dp,
                        end = 16.dp,
                        bottom = innerPadding.calculateBottomPadding() + 8.dp
                    )
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.login_section_title),
                    style = MaterialTheme.typography.titleMedium
                )

                ExposedDropdownMenuBox(
                    expanded = serverExpanded,
                    onExpandedChange = {
                        if (!uiState.isLoggedIn) serverExpanded = !serverExpanded
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = uiState.url,
                        onValueChange = {},
                        readOnly = true,
                        enabled = !uiState.isLoggedIn,
                        label = { Text(stringResource(id = R.string.prompt_url)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = serverExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = serverExpanded,
                        onDismissRequest = { serverExpanded = false }
                    ) {
                        apiEndpoints.forEach { endpoint ->
                            DropdownMenuItem(
                                text = { Text(text = endpoint) },
                                onClick = {
                                    viewModel.setUrl(endpoint)
                                    onUrlChanged(endpoint)
                                    serverExpanded = false
                                }
                            )
                        }
                    }
                }

                if (uiState.isLoggedIn) {
                    Text(uiState.status)
                    Button(onClick = onLogoutButtonClicked, modifier = Modifier.fillMaxWidth()) {
                        Text("Log out")
                    }
                } else {
                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = { viewModel.setEmail(it) },
                        label = { Text(stringResource(id = R.string.prompt_email)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Next,
                            keyboardType = KeyboardType.Email
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier.fillMaxWidth().autofill(
                            autofillTypes = listOf(AutofillType.EmailAddress),
                            onFill = { viewModel.setEmail(it) },
                        )
                    )
                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = { viewModel.setPassword(it) },
                        label = { Text(stringResource(id = R.string.prompt_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus(); onLoginButtonClicked() }
                        ),
                        keyboardOptions = KeyboardOptions.Default.copy(
                            imeAction = ImeAction.Done,
                            keyboardType = KeyboardType.Password
                        ),
                        modifier = Modifier.fillMaxWidth().autofill(
                            autofillTypes = listOf(AutofillType.Password),
                            onFill = { viewModel.setPassword(it) },
                        )
                    )
                    Button(
                        onClick = { focusManager.clearFocus(); onLoginButtonClicked() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(id = R.string.button_login))
                    }
                    Text(uiState.status)
                }

                Text(
                    text = stringResource(id = R.string.sync_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )

                ExposedDropdownMenuBox(
                    expanded = syncModeExpanded,
                    onExpandedChange = { syncModeExpanded = !syncModeExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = if (uiState.syncMode == LibreLinkUp.SYNC_MODE_FAST) {
                            stringResource(id = R.string.sync_mode_fast)
                        } else {
                            stringResource(id = R.string.sync_mode_standard)
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(id = R.string.sync_mode_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = syncModeExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = syncModeExpanded,
                        onDismissRequest = { syncModeExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.sync_mode_standard)) },
                            onClick = {
                                viewModel.setSyncMode(LibreLinkUp.SYNC_MODE_STANDARD)
                                onSyncModeChanged(LibreLinkUp.SYNC_MODE_STANDARD)
                                syncModeExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.sync_mode_fast)) },
                            onClick = {
                                viewModel.setSyncMode(LibreLinkUp.SYNC_MODE_FAST)
                                onSyncModeChanged(LibreLinkUp.SYNC_MODE_FAST)
                                syncModeExpanded = false
                            }
                        )
                    }
                }

                if (uiState.syncMode == LibreLinkUp.SYNC_MODE_FAST) {
                    ExposedDropdownMenuBox(
                        expanded = intervalExpanded,
                        onExpandedChange = { intervalExpanded = !intervalExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = if (uiState.fastSyncIntervalMinutes == 1) {
                                "1 minute"
                            } else {
                                stringResource(
                                    id = R.string.fast_sync_interval_value,
                                    uiState.fastSyncIntervalMinutes
                                )
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(id = R.string.fast_sync_interval_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = intervalExpanded,
                            onDismissRequest = { intervalExpanded = false }
                        ) {
                            listOf(1, 2, 3, 5, 10, 15, 30).forEach { minutes ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (minutes == 1) "1 minute"
                                            else stringResource(
                                                id = R.string.fast_sync_interval_value,
                                                minutes
                                            )
                                        )
                                    },
                                    onClick = {
                                        viewModel.setFastSyncIntervalMinutes(minutes)
                                        onFastSyncIntervalChanged(minutes)
                                        intervalExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(id = R.string.fast_sync_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = stringResource(id = R.string.standard_sync_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }

                Text(
                    text = stringResource(id = R.string.alert_settings_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )

                ExposedDropdownMenuBox(
                    expanded = alertUnitsExpanded,
                    onExpandedChange = { alertUnitsExpanded = !alertUnitsExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = if (uiState.alertUnits == GlucoseAlertSettings.UNITS_MGDL) {
                            stringResource(id = R.string.alert_units_mgdl)
                        } else {
                            stringResource(id = R.string.alert_units_mmol)
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(id = R.string.alert_units_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = alertUnitsExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = alertUnitsExpanded,
                        onDismissRequest = { alertUnitsExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.alert_units_mmol)) },
                            onClick = {
                                onAlertUnitsChanged(GlucoseAlertSettings.UNITS_MMOL)
                                alertUnitsExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.alert_units_mgdl)) },
                            onClick = {
                                onAlertUnitsChanged(GlucoseAlertSettings.UNITS_MGDL)
                                alertUnitsExpanded = false
                            }
                        )
                    }
                }

                Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(id = R.string.low_alert_enabled))
            Switch(
                checked = uiState.lowAlertEnabled,
                onCheckedChange = { viewModel.setLowAlertEnabled(it) }
            )
        }
        OutlinedTextField(
            value = uiState.lowAlertThreshold,
            onValueChange = { viewModel.setLowAlertThreshold(it) },
            enabled = uiState.lowAlertEnabled,
            label = { Text(stringResource(id = R.string.low_alert_threshold)) },
            suffix = { Text(if (uiState.alertUnits == GlucoseAlertSettings.UNITS_MGDL) "mg/dL" else "mmol/L") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.lowHysteresis,
            onValueChange = { viewModel.setLowHysteresis(it) },
            enabled = uiState.lowAlertEnabled,
            label = { Text(stringResource(id = R.string.rearm_margin)) },
            suffix = { Text(if (uiState.alertUnits == GlucoseAlertSettings.UNITS_MGDL) "mg/dL" else "mmol/L") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(id = R.string.persistent_vibration))
            Switch(
                checked = uiState.lowPersistentVibration,
                onCheckedChange = { viewModel.setLowPersistentVibration(it) },
                enabled = uiState.lowAlertEnabled
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(id = R.string.repeat_alert))
            Switch(
                checked = uiState.lowRepeatEnabled,
                onCheckedChange = { viewModel.setLowRepeatEnabled(it) },
                enabled = uiState.lowAlertEnabled
            )
        }
        ExposedDropdownMenuBox(
            expanded = lowRepeatExpanded,
            onExpandedChange = {
                if (uiState.lowAlertEnabled && uiState.lowRepeatEnabled) {
                    lowRepeatExpanded = !lowRepeatExpanded
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = stringResource(id = R.string.repeat_interval_value, uiState.lowRepeatIntervalMinutes),
                onValueChange = {},
                readOnly = true,
                enabled = uiState.lowAlertEnabled && uiState.lowRepeatEnabled,
                label = { Text(stringResource(id = R.string.repeat_interval)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = lowRepeatExpanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = lowRepeatExpanded,
                onDismissRequest = { lowRepeatExpanded = false }
            ) {
                listOf(1, 2, 5, 10, 15, 30, 60).forEach { minutes ->
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.repeat_interval_value, minutes)) },
                        onClick = {
                            viewModel.setLowRepeatIntervalMinutes(minutes)
                            lowRepeatExpanded = false
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(id = R.string.high_alert_enabled))
            Switch(
                checked = uiState.highAlertEnabled,
                onCheckedChange = { viewModel.setHighAlertEnabled(it) }
            )
        }
        OutlinedTextField(
            value = uiState.highAlertThreshold,
            onValueChange = { viewModel.setHighAlertThreshold(it) },
            enabled = uiState.highAlertEnabled,
            label = { Text(stringResource(id = R.string.high_alert_threshold)) },
            suffix = { Text(if (uiState.alertUnits == GlucoseAlertSettings.UNITS_MGDL) "mg/dL" else "mmol/L") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.highHysteresis,
            onValueChange = { viewModel.setHighHysteresis(it) },
            enabled = uiState.highAlertEnabled,
            label = { Text(stringResource(id = R.string.rearm_margin)) },
            suffix = { Text(if (uiState.alertUnits == GlucoseAlertSettings.UNITS_MGDL) "mg/dL" else "mmol/L") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(id = R.string.persistent_vibration))
            Switch(
                checked = uiState.highPersistentVibration,
                onCheckedChange = { viewModel.setHighPersistentVibration(it) },
                enabled = uiState.highAlertEnabled
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(id = R.string.repeat_alert))
            Switch(
                checked = uiState.highRepeatEnabled,
                onCheckedChange = { viewModel.setHighRepeatEnabled(it) },
                enabled = uiState.highAlertEnabled
            )
        }
        ExposedDropdownMenuBox(
            expanded = highRepeatExpanded,
            onExpandedChange = {
                if (uiState.highAlertEnabled && uiState.highRepeatEnabled) {
                    highRepeatExpanded = !highRepeatExpanded
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = stringResource(id = R.string.repeat_interval_value, uiState.highRepeatIntervalMinutes),
                onValueChange = {},
                readOnly = true,
                enabled = uiState.highAlertEnabled && uiState.highRepeatEnabled,
                label = { Text(stringResource(id = R.string.repeat_interval)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = highRepeatExpanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = highRepeatExpanded,
                onDismissRequest = { highRepeatExpanded = false }
            ) {
                listOf(1, 2, 5, 10, 15, 30, 60).forEach { minutes ->
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.repeat_interval_value, minutes)) },
                        onClick = {
                            viewModel.setHighRepeatIntervalMinutes(minutes)
                            highRepeatExpanded = false
                        }
                    )
                }
            }
        }

                Button(
                    onClick = onSaveAlertSettings,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(stringResource(id = R.string.save_alert_settings))
                }
                Text(
                    text = stringResource(id = R.string.alert_settings_warning),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )

                if (!uiState.isIgnoringBatteryOptimizations) {
                    Text(
                        text = stringResource(id = R.string.battery_restricted),
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = onDisableBatteryRestrictionsButtonClicked,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(id = R.string.disable_battery_restrictions))
                    }
                }
                Text(uiState.version)
            }
        }
    }
}

@Preview(showBackground = true, name = "Light")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, name = "Dark")
@Composable
fun Preview() {
    MainView()
}
