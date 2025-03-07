/*
 * Copyright (c) 2013-2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.platform.internal;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.UserHandle;
import android.util.Log;
import lineageos.app.Profile;
import lineageos.app.Profile.ProfileTrigger;
import lineageos.app.ProfileManager;
import lineageos.providers.LineageSettings;

import java.util.UUID;

/**
 * @hide
 */
public class ProfileTriggerHelper extends BroadcastReceiver {
    private static final String TAG = "ProfileTriggerHelper";

    private Context mContext;
    private ProfileManagerService mManagerService;

    private WifiManager mWifiManager;
    private String mLastConnectedSSID;

    private IntentFilter mIntentFilter;
    private boolean mFilterRegistered = false;

    private class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateEnabled();
        }
    }
    private final ContentObserver mSettingsObserver;

    public ProfileTriggerHelper(Context context, Handler handler,
            ProfileManagerService profileManagerService) {
        mContext = context;
        mManagerService = profileManagerService;
        mSettingsObserver = new SettingsObserver(handler);

        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mLastConnectedSSID = getActiveSSID();

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        mIntentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
       // mIntentFilter.addAction(AudioManager.A2DP_ROUTE_CHANGED_ACTION);
        updateEnabled();

        mContext.getContentResolver().registerContentObserver(
                LineageSettings.System.getUriFor(LineageSettings.System.SYSTEM_PROFILES_ENABLED), false,
                mSettingsObserver);
    }

    public void updateEnabled() {
        boolean enabled = LineageSettings.System.getInt(mContext.getContentResolver(),
                LineageSettings.System.SYSTEM_PROFILES_ENABLED, 1) == 1;
        if (enabled && !mFilterRegistered) {
            Log.v(TAG, "Enabling");
            mContext.registerReceiver(this, mIntentFilter);
            mFilterRegistered = true;
        } else if (!enabled && mFilterRegistered) {
            Log.v(TAG, "Disabling");
            mContext.unregisterReceiver(this);
            mFilterRegistered = false;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
            NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            NetworkInfo.DetailedState state = networkInfo.getDetailedState();
            if (NetworkInfo.DetailedState.DISCONNECTED.equals(state)) {
                checkTriggers(Profile.TriggerType.WIFI, mLastConnectedSSID,
                        Profile.TriggerState.ON_DISCONNECT);
                mLastConnectedSSID = WifiManager.UNKNOWN_SSID;
            } else if (NetworkInfo.DetailedState.CONNECTED.equals(state)) {
                String ssid = getActiveSSID();
                if (ssid != null) {
                    mLastConnectedSSID = ssid;
                    checkTriggers(Profile.TriggerType.WIFI, mLastConnectedSSID,
                            Profile.TriggerState.ON_CONNECT);
                }
            }
        } else if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)
                || action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
            int triggerState = action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)
                    ? Profile.TriggerState.ON_CONNECT : Profile.TriggerState.ON_DISCONNECT;
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            checkTriggers(Profile.TriggerType.BLUETOOTH, device.getAddress(), triggerState);
/*        } else if (action.equals(AudioManager.A2DP_ROUTE_CHANGED_ACTION)) {
            BluetoothDevice device = intent
                    .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, 0);
            int triggerState = (state == BluetoothProfile.STATE_CONNECTED)
                    ? Profile.TriggerState.ON_A2DP_CONNECT :
                    Profile.TriggerState.ON_A2DP_DISCONNECT;

            checkTriggers(Profile.TriggerType.BLUETOOTH, device.getAddress(), triggerState);*/
        }
    }

    private void checkTriggers(int type, String id, int newState) {
        final Profile activeProfile = mManagerService.getActiveProfileInternal();
        final UUID currentProfileUuid = activeProfile.getUuid();

        boolean newProfileSelected = false;
        for (Profile p : mManagerService.getProfileList()) {
            final int profileTriggerState = p.getTriggerState(type, id);
            if (newState != profileTriggerState) {
                    continue;
            }

            if (!currentProfileUuid.equals(p.getUuid())) {
                mManagerService.setActiveProfileInternal(p, true);
                newProfileSelected = true;
            }
        }

        if (!newProfileSelected) {
            //Does the active profile actually cares about this event?
            for (ProfileTrigger trigger : activeProfile.getTriggersFromType(type)) {
                final String triggerID = trigger.getId();
                if (triggerID.equals(id)) {
                    Intent intent
                            = new Intent(ProfileManager.INTENT_ACTION_PROFILE_TRIGGER_STATE_CHANGED);
                    intent.putExtra(ProfileManager.EXTRA_TRIGGER_ID, id);
                    intent.putExtra(ProfileManager.EXTRA_TRIGGER_TYPE, type);
                    intent.putExtra(ProfileManager.EXTRA_TRIGGER_STATE, newState);
                    mContext.sendBroadcastAsUser(intent, UserHandle.ALL);

                    final int triggerState = trigger.getState();
                    if ((newState == Profile.TriggerState.ON_CONNECT
                            && triggerState == Profile.TriggerState.ON_CONNECT) ||
                            (newState == Profile.TriggerState.ON_DISCONNECT
                            && triggerState == Profile.TriggerState.ON_DISCONNECT)) {
                        activeProfile.doSelect(mContext, null);
                    }
                    break;
                }
            }

        }
    }

    private String removeDoubleQuotes(String string) {
        final int length = string.length();
        if (length >= 2) {
            if (string.startsWith("\"") && string.endsWith("\"")) {
                return string.substring(1, length - 1);
            }
        }
        return string;
    }

    private String getActiveSSID() {
        WifiInfo wifiinfo = mWifiManager.getConnectionInfo();
        if (wifiinfo == null) {
            return null;
        }
        return removeDoubleQuotes(wifiinfo.getSSID());
    }
}
