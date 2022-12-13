/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.server.location.gnss;

import android.content.Context;
import android.os.Looper;

/**
 * An abstraction for use by {@link GnssLocationProvider}. This class allows switching between
 * implementations with a compile-time constant change, which is less risky than rolling back a
 * whole class. When there is a single implementation again this class can be replaced by that
 * implementation.
 */
abstract class NetworkTimeHelper {

    /**
     * The callback interface used by {@link NetworkTimeHelper} to report the time to {@link
     * GnssLocationProvider}. The callback can happen at any time using the thread associated with
     * the looper passed to {@link #create(Context, Looper, InjectTimeCallback)}.
     */
    interface InjectTimeCallback {
        void injectTime(long unixEpochTimeMillis, long elapsedRealtimeMillis,
                int uncertaintyMillis);
    }

    /**
     * Creates the {@link NetworkTimeHelper} instance for use by {@link GnssLocationProvider}.
     */
    static NetworkTimeHelper create(
            Context context, Looper looper, InjectTimeCallback injectTimeCallback) {
        return new NtpNetworkTimeHelper(context, looper, injectTimeCallback);
    }

    /**
     * Sets the "on demand time injection" mode.
     *
     * <p>Called by {@link GnssLocationProvider} to set the expected time injection behavior.
     * When {@code enablePeriodicTimeInjection == true}, the time helper should periodically send
     * the time on an undefined schedule. The time can be injected at other times for other reasons
     * as well as be requested via {@link #demandUtcTimeInjection()}.
     *
     * @param periodicTimeInjectionEnabled {@code true} if the GNSS implementation requires periodic
     *   time signals
     */
    abstract void setPeriodicTimeInjectionMode(boolean periodicTimeInjectionEnabled);

    /**
     * Requests an asynchronous time injection via {@link InjectTimeCallback#injectTime}, if a
     * network time is available. {@link InjectTimeCallback#injectTime} may not be called if a
     * network time is not available.
     */
    abstract void demandUtcTimeInjection();

    /**
     * Notifies that network connectivity has been established.
     *
     * <p>Called by {@link GnssLocationProvider} when the device establishes a data network
     * connection.
     */
    abstract void onNetworkAvailable();

}
