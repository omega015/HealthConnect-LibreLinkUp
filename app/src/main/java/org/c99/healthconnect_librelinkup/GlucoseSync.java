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
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.records.BloodGlucoseRecord;
import androidx.health.connect.client.records.metadata.DataOrigin;
import androidx.health.connect.client.records.metadata.Metadata;
import androidx.health.connect.client.response.InsertRecordsResponse;
import androidx.health.connect.client.units.BloodGlucose;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import kotlin.ResultKt;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.coroutines.intrinsics.IntrinsicsKt;

/**
 * Performs one LibreLinkUp -> Health Connect -> Wear OS synchronization.
 *
 * Both WorkManager's standard background mode and FastSyncService call this class so the actual
 * glucose handling stays identical regardless of how the synchronization was triggered.
 */
public final class GlucoseSync {
    private static final String TAG = "LibreLinkUpSync";
    private static final long HEALTH_CONNECT_INSERT_TIMEOUT_SECONDS = 30;

    private static final String GLUCOSE_KEY = "org.c99.healthconnect_librelinkup.glucose";
    private static final String TREND_ARROW_KEY = "org.c99.healthconnect_librelinkup.trendArrow";
    private static final String COLOR_KEY = "org.c99.healthconnect_librelinkup.color";
    private static final String UNITS_KEY = "org.c99.healthconnect_librelinkup.units";
    private static final String TIMESTAMP_KEY = "org.c99.healthconnect_librelinkup.timestamp";

    private static final String SYNC_STATE_PREFS = "glucose_sync_state";
    private static final String KEY_LAST_HEALTH_CONNECT_TIMESTAMP = "last_health_connect_factory_timestamp";
    private static final String KEY_LAST_WEAR_TIMESTAMP = "last_wear_factory_timestamp";

    private GlucoseSync() {
    }

    public static final class SyncResult {
        public final boolean success;
        public final String errorMessage;

        private SyncResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public static SyncResult success() {
            return new SyncResult(true, null);
        }

        public static SyncResult failure(Exception exception) {
            String message = exception.getMessage();
            if (message == null || message.isEmpty()) {
                message = exception.getClass().getSimpleName();
            }
            return new SyncResult(false, message);
        }
    }

    public static SyncResult perform(@NonNull Context context) {
        Context applicationContext = context.getApplicationContext();
        LibreLinkUp libreLinkUp = new LibreLinkUp(applicationContext);

        try {
            LibreLinkUp.ConnectionsResult result = libreLinkUp.connections();
            libreLinkUp.setAuthTicket(result.ticket);
            LibreLinkUp.GlucoseMeasurement gm = result.data.get(0).glucoseMeasurement;

            Log.i(
                    TAG,
                    "Fetched glucose=" + gm.Value
                            + " mg/dL=" + gm.ValueInMgPerDl
                            + " timestamp=" + gm.FactoryTimestamp
                            + " trend=" + gm.TrendArrow
            );

            String factoryTimestamp = gm.FactoryTimestamp;
            boolean hasFactoryTimestamp = factoryTimestamp != null && !factoryTimestamp.isEmpty();
            SharedPreferences syncState = applicationContext.getSharedPreferences(
                    SYNC_STATE_PREFS,
                    Context.MODE_PRIVATE
            );

            String lastHealthConnectTimestamp = syncState.getString(
                    KEY_LAST_HEALTH_CONNECT_TIMESTAMP,
                    null
            );
            String lastWearTimestamp = syncState.getString(KEY_LAST_WEAR_TIMESTAMP, null);

            boolean healthConnectNeedsUpdate = !hasFactoryTimestamp
                    || !factoryTimestamp.equals(lastHealthConnectTimestamp);
            boolean wearNeedsUpdate = !hasFactoryTimestamp
                    || !factoryTimestamp.equals(lastWearTimestamp);

            if (!healthConnectNeedsUpdate && !wearNeedsUpdate) {
                Log.i(
                        TAG,
                        "Duplicate FactoryTimestamp=" + factoryTimestamp
                                + "; skipping Health Connect and Wear update"
                );
                return SyncResult.success();
            }

            Exception healthConnectFailure = null;
            if (healthConnectNeedsUpdate) {
                ZonedDateTime time = parseMeasurementTime(factoryTimestamp);
                HealthConnectClient healthConnectClient = HealthConnectClient.getOrCreate(applicationContext);

                BloodGlucoseRecord record = new BloodGlucoseRecord(
                        Instant.from(time),
                        time.getOffset(),
                        BloodGlucose.milligramsPerDeciliter(gm.ValueInMgPerDl),
                        BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID,
                        0,
                        BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN,
                        new Metadata(
                                "",
                                new DataOrigin(applicationContext.getPackageName()),
                                Instant.from(time),
                                null,
                                0,
                                null,
                                0
                        )
                );

                try {
                    insertHealthConnectRecord(healthConnectClient, record);

                    if (hasFactoryTimestamp) {
                        syncState.edit()
                                .putString(KEY_LAST_HEALTH_CONNECT_TIMESTAMP, factoryTimestamp)
                                .apply();
                    }

                    Log.i(
                            TAG,
                            "Health Connect insert confirmed timestamp=" + factoryTimestamp
                    );
                } catch (Exception exception) {
                    healthConnectFailure = exception;
                    Log.e(
                            TAG,
                            "Health Connect insert failed; reading will be retried",
                            exception
                    );
                }
            } else {
                Log.i(
                        TAG,
                        "Duplicate FactoryTimestamp=" + factoryTimestamp
                                + "; skipping Health Connect insert"
                );
            }

            if (wearNeedsUpdate) {
                boolean wearSent = sendToWear(applicationContext, gm);
                if (wearSent && hasFactoryTimestamp) {
                    syncState.edit()
                            .putString(KEY_LAST_WEAR_TIMESTAMP, factoryTimestamp)
                            .apply();
                }
            } else {
                Log.i(
                        TAG,
                        "Duplicate FactoryTimestamp=" + factoryTimestamp
                                + "; skipping Wear update"
                );
            }

            if (healthConnectFailure != null) {
                return SyncResult.failure(healthConnectFailure);
            }

            return SyncResult.success();
        } catch (Exception exception) {
            Log.e(TAG, "Glucose sync failed", exception);
            return SyncResult.failure(exception);
        }
    }

    private static void insertHealthConnectRecord(
            HealthConnectClient healthConnectClient,
            BloodGlucoseRecord record) throws Exception {
        CountDownLatch completion = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Continuation<InsertRecordsResponse> continuation = new Continuation<InsertRecordsResponse>() {
            @NonNull
            @Override
            public CoroutineContext getContext() {
                return EmptyCoroutineContext.INSTANCE;
            }

            @Override
            public void resumeWith(@NonNull Object result) {
                try {
                    ResultKt.throwOnFailure(result);
                } catch (Throwable throwable) {
                    failure.set(throwable);
                } finally {
                    completion.countDown();
                }
            }
        };

        Object insertResult = healthConnectClient.insertRecords(
                Collections.singletonList(record),
                continuation
        );

        if (insertResult != IntrinsicsKt.getCOROUTINE_SUSPENDED()) {
            ResultKt.throwOnFailure(insertResult);
            return;
        }

        try {
            if (!completion.await(HEALTH_CONNECT_INSERT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new TimeoutException(
                        "Health Connect insert did not complete within "
                                + HEALTH_CONNECT_INSERT_TIMEOUT_SECONDS + " seconds"
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        }

        Throwable throwable = failure.get();
        if (throwable != null) {
            if (throwable instanceof Exception) {
                throw (Exception) throwable;
            }
            throw new RuntimeException("Health Connect insert failed", throwable);
        }
    }

    private static ZonedDateTime parseMeasurementTime(String factoryTimestamp) {
        if (factoryTimestamp == null) {
            return ZonedDateTime.now();
        }

        try {
            return ZonedDateTime.parse(
                    factoryTimestamp + " +0000",
                    DateTimeFormatter.ofPattern("M/d/y h:m:s a Z", Locale.US)
            ).withZoneSameInstant(ZoneId.systemDefault());
        } catch (DateTimeParseException exception) {
            try {
                long timestampMillis = Long.parseLong(factoryTimestamp);
                return ZonedDateTime.ofInstant(
                        Instant.ofEpochMilli(timestampMillis),
                        ZoneId.systemDefault()
                );
            } catch (NumberFormatException ignored) {
                return ZonedDateTime.now();
            }
        }
    }

    private static boolean sendToWear(Context context, LibreLinkUp.GlucoseMeasurement gm) {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
                != com.google.android.gms.common.ConnectionResult.SUCCESS) {
            Log.w(TAG, "Google Play Services unavailable; skipping Wear transfer");
            return false;
        }

        try {
            DataClient dataClient = Wearable.getDataClient(context);
            Task<Void> availabilityTask = GoogleApiAvailability.getInstance()
                    .checkApiAvailability(dataClient);
            Tasks.await(availabilityTask);

            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/glucose");
            putDataMapRequest.getDataMap().putFloat(GLUCOSE_KEY, gm.Value);
            putDataMapRequest.getDataMap().putInt(COLOR_KEY, gm.MeasurementColor);
            putDataMapRequest.getDataMap().putInt(TREND_ARROW_KEY, gm.TrendArrow);
            putDataMapRequest.getDataMap().putInt(UNITS_KEY, gm.GlucoseUnits);
            putDataMapRequest.getDataMap().putString(TIMESTAMP_KEY, gm.FactoryTimestamp);
            PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest().setUrgent();
            Tasks.await(dataClient.putDataItem(putDataRequest));

            Log.i(
                    TAG,
                    "Sent to Wear glucose=" + gm.Value
                            + " timestamp=" + gm.FactoryTimestamp
            );
            return true;
        } catch (Exception exception) {
            // A Wear transfer failure should not discard the successful Libre/Health Connect sync.
            Log.e(TAG, "Wear transfer failed", exception);
            return false;
        }
    }
}
