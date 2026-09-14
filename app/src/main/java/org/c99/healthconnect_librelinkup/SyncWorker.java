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

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class SyncWorker extends Worker {
    public SyncWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        LibreLinkUp libreLinkUp = new LibreLinkUp(getApplicationContext());

        // If the user changed to Fast mode while a standard WorkManager job was still queued,
        // quietly finish it rather than running a second sync path alongside the foreground service.
        if (LibreLinkUp.SYNC_MODE_FAST.equals(libreLinkUp.getSyncMode())) {
            return Result.success();
        }

        GlucoseSync.SyncResult result = GlucoseSync.perform(getApplicationContext());
        return result.success ? Result.success() : Result.retry();
    }
}
