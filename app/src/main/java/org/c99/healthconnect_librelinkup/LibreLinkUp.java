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

package org.c99.healthconnect_librelinkup;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.Toast;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LibreLinkUp {
    public static final String SYNC_WORK_NAME = "glucose-sync";
    public static final String SYNC_MODE_STANDARD = "standard";
    public static final String SYNC_MODE_FAST = "fast";
    public static final int DEFAULT_FAST_SYNC_INTERVAL_MINUTES = 5;
    public static final long STANDARD_SYNC_INTERVAL_MINUTES = 15;

    private static final String SETTINGS_PREFS = "sync_settings";
    private static final String KEY_SYNC_MODE = "sync_mode";
    private static final String KEY_FAST_SYNC_INTERVAL = "fast_sync_interval_minutes";

    private AuthTicket authTicket;
    private User user;
    private final Context context;
    private String LIBRELINKUP_URL = "https://api.libreview.io";

    private final OkHttpClient client = new OkHttpClient();
    private final Moshi moshi = new Moshi.Builder().build();

    private final String LIBRELINKUP_VERSION = "4.16.0";
    private final String LIBRELINKUP_PRODUCT = "llu.ios";
    private final Headers LIBRELINKUP_HEADERS = new Headers.Builder()
            .add("Content-Type", "application/json")
            .add("version", LIBRELINKUP_VERSION)
            .add("product", LIBRELINKUP_PRODUCT)
            .build();

    private SharedPreferences getEncryptedSharedPreferences() throws GeneralSecurityException, IOException {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();

        return EncryptedSharedPreferences.create(
                context,
                "cache",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }

    private SharedPreferences getSyncSettings() {
        return context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
    }

    public String getSyncMode() {
        return getSyncSettings().getString(KEY_SYNC_MODE, SYNC_MODE_STANDARD);
    }

    public void setSyncMode(String mode) {
        String safeMode = SYNC_MODE_FAST.equals(mode) ? SYNC_MODE_FAST : SYNC_MODE_STANDARD;
        getSyncSettings().edit().putString(KEY_SYNC_MODE, safeMode).apply();
    }

    public int getFastSyncIntervalMinutes() {
        return sanitizeFastInterval(
                getSyncSettings().getInt(KEY_FAST_SYNC_INTERVAL, DEFAULT_FAST_SYNC_INTERVAL_MINUTES)
        );
    }

    public void setFastSyncIntervalMinutes(int minutes) {
        getSyncSettings().edit()
                .putInt(KEY_FAST_SYNC_INTERVAL, sanitizeFastInterval(minutes))
                .apply();
    }

    private int sanitizeFastInterval(int minutes) {
        if (minutes == 1 || minutes == 2 || minutes == 3 || minutes == 5
                || minutes == 10 || minutes == 15 || minutes == 30) {
            return minutes;
        }
        return DEFAULT_FAST_SYNC_INTERVAL_MINUTES;
    }

    public void schedule() {
        if (!hasValidAuthTicket()) {
            stopAllSync();
            return;
        }

        if (SYNC_MODE_FAST.equals(getSyncMode())) {
            startFastSync();
        } else {
            startStandardSync();
        }
    }

    public void applySyncSettings() {
        schedule();
    }

    private boolean hasValidAuthTicket() {
        return authTicket != null && authTicket.token != null && !authTicket.token.isEmpty();
    }

    private void startStandardSync() {
        stopFastSync();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                SyncWorker.class,
                STANDARD_SYNC_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        )
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .addTag("sync")
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
        );

        android.util.Log.i("LibreLinkUp", "Standard glucose sync scheduled with WorkManager");
    }

    private void startFastSync() {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME);

        Intent intent = new Intent(context, FastSyncService.class);
        intent.setAction(FastSyncService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }

        int interval = getFastSyncIntervalMinutes();
        Toast.makeText(
                context,
                "Fast glucose sync enabled every " + interval + " minutes",
                Toast.LENGTH_SHORT
        ).show();
        android.util.Log.i("LibreLinkUp", "Fast glucose sync enabled every " + interval + " minutes");
    }

    private void stopFastSync() {
        Intent intent = new Intent(context, FastSyncService.class);
        intent.setAction(FastSyncService.ACTION_STOP);
        context.stopService(intent);
    }

    public void stopAllSync() {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME);
        stopFastSync();
    }

    public LibreLinkUp(Context context) {
        this.context = context.getApplicationContext();
        try {
            SharedPreferences cache = getEncryptedSharedPreferences();

            LIBRELINKUP_URL = cache.getString("url", "https://api.libreview.io");

            authTicket = new AuthTicket();
            authTicket.token = cache.getString("auth_token", null);
            authTicket.duration = cache.getLong("auth_duration", 0);
            authTicket.expires = cache.getLong("auth_expires", 0);

            user = new User();
            user.id = cache.getString("user_id", null);
            user.email = cache.getString("user_email", null);
            user.firstName = cache.getString("user_first_name", null);
            user.lastName = cache.getString("user_last_name", null);
        } catch (Exception e) {
            authTicket = null;
            user = null;
            e.printStackTrace();
        }
    }

    public AuthTicket getAuthTicket() {
        return authTicket;
    }

    public String getUrl() {
        return LIBRELINKUP_URL;
    }

    public void setUrl(String url) {
        LIBRELINKUP_URL = url;

        try {
            SharedPreferences.Editor cache = getEncryptedSharedPreferences().edit();
            cache.putString("url", url);
            cache.apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setAuthTicket(AuthTicket ticket) {
        try {
            SharedPreferences.Editor cache = getEncryptedSharedPreferences().edit();
            if(ticket != null) {
                cache.putString("auth_token", ticket.token);
                cache.putLong("auth_duration", ticket.duration);
                cache.putLong("auth_expires", ticket.expires);
            } else {
                cache.remove("auth_token");
                cache.remove("auth_duration");
                cache.remove("auth_expires");
            }
            cache.apply();
            authTicket = ticket;
        } catch (Exception e) {
            authTicket = null;
        }
    }

    public void setUser(User user) {
        try {
            SharedPreferences.Editor cache = getEncryptedSharedPreferences().edit();
            if(user != null) {
                cache.putString("user_id", user.id);
                cache.putString("user_email", user.email);
                cache.putString("user_first_name", user.firstName);
                cache.putString("user_last_name", user.lastName);
            } else {
                cache.remove("user_id");
                cache.remove("user_email");
                cache.remove("user_first_name");
                cache.remove("user_last_name");
            }
            cache.apply();
            this.user = user;
        } catch (Exception e) {
            this.user = null;
        }
    }

    public User getUser() {
        return user;
    }

    public String AccountID() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hashedBytes = digest.digest(user.id.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashedBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public LoginResult login(String email, String password) throws IOException {
        JsonAdapter<LoginResult> loginResultJsonAdapter = moshi.adapter(LoginResult.class);

        JSONObject loginRequest = new JSONObject();
        try {
            loginRequest.put("email", email);
            loginRequest.put("password", password);
        } catch (Exception e) {
            throw new IOException("Unable to create LibreLinkUp login request", e);
        }

        for (int redirectCount = 0; redirectCount <= 3; redirectCount++) {
            Request request = new Request.Builder()
                    .url(LIBRELINKUP_URL + "/llu/auth/login")
                    .headers(LIBRELINKUP_HEADERS)
                    .post(RequestBody.create(
                            MediaType.parse("application/json; charset=utf-8"),
                            loginRequest.toString()
                    ))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected code " + response);
                }

                if (response.body() == null) {
                    throw new IOException("Empty LibreLinkUp login response");
                }

                String responseBody = response.body().string();
                JSONObject responseJson;
                try {
                    responseJson = new JSONObject(responseBody);
                } catch (Exception e) {
                    throw new IOException("Invalid LibreLinkUp login response", e);
                }

                int status = responseJson.optInt("status", -1);
                JSONObject dataJson = responseJson.optJSONObject("data");
                boolean redirect = false;
                String region = null;
                boolean hasUser = false;
                boolean hasAuthTicket = false;

                if (dataJson != null) {
                    Object redirectValue = dataJson.opt("redirect");
                    if (redirectValue instanceof Boolean) {
                        redirect = (Boolean) redirectValue;
                    } else if (redirectValue != null) {
                        redirect = Boolean.parseBoolean(String.valueOf(redirectValue));
                    }
                    region = dataJson.optString("region", null);
                    hasUser = dataJson.optJSONObject("user") != null;
                    hasAuthTicket = dataJson.optJSONObject("authTicket") != null;
                }

                android.util.Log.i(
                        "LibreLinkUp",
                        "Login response: status=" + status
                                + " redirect=" + redirect
                                + " region=" + (region == null || region.isEmpty() ? "none" : region)
                                + " hasUser=" + hasUser
                                + " hasAuthTicket=" + hasAuthTicket
                );

                if (status == 0 && redirect) {
                    if (region == null || region.trim().isEmpty()
                            || !region.trim().matches("[A-Za-z0-9-]+")) {
                        throw new IOException("LibreLinkUp returned an invalid redirect region");
                    }

                    String normalizedRegion = region.trim().toLowerCase(Locale.US);
                    String redirectedUrl = "https://api-" + normalizedRegion + ".libreview.io";
                    if (redirectedUrl.equalsIgnoreCase(LIBRELINKUP_URL)) {
                        throw new IOException("LibreLinkUp regional redirect loop detected");
                    }

                    android.util.Log.i(
                            "LibreLinkUp",
                            "Libre login redirected to region " + normalizedRegion
                                    + " (" + redirectedUrl + ")"
                    );
                    setUrl(redirectedUrl);
                    continue;
                }

                LoginResult result = loginResultJsonAdapter.fromJson(responseBody);
                if (result == null) {
                    throw new IOException("Unable to parse LibreLinkUp login response");
                }
                return result;
            }
        }

        throw new IOException("Too many LibreLinkUp regional redirects");
    }

    public ConnectionsResult connections() throws IOException {
        JsonAdapter<ConnectionsResult> connectionsResultJsonAdapter = moshi.adapter(ConnectionsResult.class);
        Headers headers = new Headers.Builder().addAll(LIBRELINKUP_HEADERS)
                .add("Authorization", "Bearer " + authTicket.token)
                .add("Account-Id", AccountID())
                .build();

        Request request = new Request.Builder()
                .url(LIBRELINKUP_URL + "/llu/connections")
                .headers(headers)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful())
                throw new IOException("Unexpected code " + response);

            return connectionsResultJsonAdapter.fromJson(response.body().string());
        }
    }

    public static class User {
        public String id;
        public String firstName;
        public String lastName;
        public String email;
    }

    public static class AuthTicket {
        public String token;
        public long expires;
        public long duration;
    }

    public static class Sensor {
        public String deviceId;
        public String sn;
    }

    public static class GlucoseMeasurement {
        public String FactoryTimestamp;
        public String Timestamp;
        public int type;
        public int ValueInMgPerDl;
        public int TrendArrow;
        public String TrendMessage;
        public int MeasurementColor;
        public int GlucoseUnits;
        public float Value;
        public boolean isHigh;
        public boolean isLow;
    }

    public static class Connection {
        public String id;
        public String patientId;
        public String country;
        public int status;
        public String firstName;
        public String lastName;
        public Sensor sensor;
        public GlucoseMeasurement glucoseMeasurement;
        public GlucoseMeasurement glucoseItem;
    }

    public static class LibreLinkUpError {
        public String message;
    }

    public static class LibreLinkUpResult {
        public int status;
        public LibreLinkUpError error;
    }

    public static class LoginResult extends LibreLinkUpResult {
        public static class LoginResultData {
            User user;
            AuthTicket authTicket;
            boolean redirect;
            String region;
        };
        LoginResultData data;
    }

    public static class ConnectionsResult extends LibreLinkUpResult {
        List<Connection> data;
        AuthTicket ticket;
    }
}
