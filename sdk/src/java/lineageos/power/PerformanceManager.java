/*
 * Copyright (C) 2015 The CyanogenMod Project
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
package lineageos.power;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.util.Arrays;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

import lineageos.app.LineageContextConstants;

/**
 *
 */
public class PerformanceManager {

    public static final String TAG = "PerformanceManager";

    /**
     * Power save profile
     *
     * This mode sacrifices performance for maximum power saving.
     */
    public static final int PROFILE_POWER_SAVE = 0;

    /**
     * Balanced power profile
     * 
     * The default mode for balanced power savings and performance
     */
    public static final int PROFILE_BALANCED = 1;

    /**
     * High-performance profile
     * 
     * This mode sacrifices power for maximum performance
     */
    public static final int PROFILE_HIGH_PERFORMANCE = 2;

    /**
     * Power save bias profile
     * 
     * This mode decreases performance slightly to improve
     * power savings. 
     */
    public static final int PROFILE_BIAS_POWER_SAVE = 3;
    
    /**
     * Performance bias profile
     * 
     * This mode improves performance at the cost of some power.
     */
    public static final int PROFILE_BIAS_PERFORMANCE = 4;

    /**
     * @hide
     */
    public static final int[] POSSIBLE_POWER_PROFILES = new int[] {
            PROFILE_POWER_SAVE,
            PROFILE_BALANCED,
            PROFILE_HIGH_PERFORMANCE,
            PROFILE_BIAS_POWER_SAVE,
            PROFILE_BIAS_PERFORMANCE
    };

    private int mNumberOfProfiles = 0;

    /**
     * Broadcast sent when profile is changed
     */
    public static final String POWER_PROFILE_CHANGED = "lineageos.power.PROFILE_CHANGED";

    private static IPerformanceManager sService;
    private static PerformanceManager sInstance;

    private PerformanceManager(Context context) {
        sService = getService();
        if (context.getPackageManager().hasSystemFeature(
                LineageContextConstants.Features.PERFORMANCE) && sService == null) {
            Log.wtf(TAG, "Unable to get PerformanceManagerService. The service" +
                    " either crashed, was not started, or the interface has been called to early" +
                    " in SystemServer init");
        }
        try {
            if (sService != null) {
                mNumberOfProfiles = sService.getNumberOfProfiles();
            }
        } catch (RemoteException e) {
        }
    }

    public static PerformanceManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new PerformanceManager(context);
        }
        return sInstance;
    }

    /** @hide */
    public static IPerformanceManager getService() {
        if (sService != null) {
            return sService;
        }
        IBinder b = ServiceManager.getService(LineageContextConstants.LINEAGE_PERFORMANCE_SERVICE);
        if (b != null) {
            sService = IPerformanceManager.Stub.asInterface(b);
            return sService;
        }
        return null;
    }

    private boolean checkService() {
        if (sService == null) {
            Log.w(TAG, "not connected to PerformanceManagerService");
            return false;
        }
        return true;
    }

    /**
     * Returns the number of supported profiles, -1 if unsupported
     * This is queried via the PowerHAL.
     */
    public int getNumberOfProfiles() {
        return mNumberOfProfiles;
    }

    /**
     * Set the system power profile
     *
     * @throws IllegalArgumentException if invalid
     */
    public boolean setPowerProfile(int profile) {
        if (mNumberOfProfiles < 1) {
            throw new IllegalArgumentException("Power profiles not enabled on this system!");
        }

        boolean changed = false;
        try {
            if (checkService()) {
                changed = sService.setPowerProfile(profile);
            }
        } catch (RemoteException e) {
            throw new IllegalArgumentException(e);
        }
        return changed;
    }

    /**
     * Set the system power profile
     *
     * @throws IllegalArgumentException if invalid
     */
    public boolean setPowerProfile(PerformanceProfile profile) {
        if (mNumberOfProfiles < 1) {
            throw new IllegalArgumentException("Power profiles not enabled on this system!");
        }

        boolean changed = false;
        try {
            if (checkService()) {
                changed = sService.setPowerProfile(profile.getId());
            }
        } catch (RemoteException e) {
            throw new IllegalArgumentException(e);
        }
        return changed;
    }

    /**
     * Gets the current power profile
     *
     * Returns -1 if power profiles are not enabled
     */
    public int getPowerProfile() {
        int ret = -1;
        if (mNumberOfProfiles > 0) {
            try {
                if (checkService()) {
                    ret = sService.getPowerProfile();
                }
            } catch (RemoteException e) {
                // nothing
            }
        }
        return ret;
    }

    /**
     * Gets the specified power profile
     *
     * Returns null if power profiles are not enabled or the profile was not found
     */
    public PerformanceProfile getPowerProfile(int profile) {
        PerformanceProfile ret = null;
        if (mNumberOfProfiles > 0) {
            try {
                if (checkService()) {
                    ret = sService.getPowerProfileById(profile);
                }
            } catch (RemoteException e) {
                // nothing
            }
        }
        return ret;
    }

    /**
     * Gets the currently active performance profile
     *
     * Returns null if no profiles are available.
     */
    public PerformanceProfile getActivePowerProfile() {
        PerformanceProfile ret = null;
        if (mNumberOfProfiles > 0) {
            try {
                if (checkService()) {
                    ret = sService.getActivePowerProfile();
                }
            } catch (RemoteException e) {
                // nothing
            }
        }
        return ret;
    }

    /**
     * Gets a set, sorted by weight, of all supported power profiles
     *
     * Returns an empty set if power profiles are not enabled
     */
    public SortedSet<PerformanceProfile> getPowerProfiles() {
        final SortedSet<PerformanceProfile> profiles = new TreeSet<PerformanceProfile>();
        if (mNumberOfProfiles > 0) {
            try {
                if (checkService()) {
                    PerformanceProfile[] p = sService.getPowerProfiles();
                    if (p != null) {
                        profiles.addAll(Arrays.asList(p));
                    }
                }
            } catch (RemoteException e) {
                // nothing
            }
        }
        return Collections.unmodifiableSortedSet(profiles);
    }
}
