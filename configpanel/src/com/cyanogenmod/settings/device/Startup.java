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

package com.cyanogenmod.settings.device;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import java.util.Map;

import com.cyanogenmod.settings.device.utils.Constants;
import com.cyanogenmod.settings.device.utils.Constants.GestureCategory;
import com.cyanogenmod.settings.device.utils.FileUtils;

public class Startup extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            boolean gesturesEnabled = hasTouchscreenGestures();
            enableComponent(context,
                TouchscreenGestureSettings.class.getName(), gesturesEnabled);
            if (gesturesEnabled) {
                // Restore gestures to saved preference values
                for (Map.Entry<String, GestureCategory> entry
                        : Constants.sGestureMap.entrySet()) {
                    boolean enabled = Constants.isPreferenceEnabled(context,
                            entry.getKey(), entry.getValue().defaultValue);
                    TouchscreenGestureSettings.setCategoryEnable(entry.getValue(), enabled);
                }
            }
        }
    }

    private boolean hasTouchscreenGestures() {
        return FileUtils.readOneLine(Constants.TOUCHSCREEN_GESTURE_LIST_NODE) != null;
    }

    private void enableComponent(Context context, String component, boolean enabled) {
        ComponentName name = new ComponentName(context, component);
        PackageManager pm = context.getPackageManager();
        int state = pm.getComponentEnabledSetting(name);
        if (enabled && state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            pm.setComponentEnabledSetting(name,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);

        } else if (!enabled && state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            pm.setComponentEnabledSetting(name,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }
}
