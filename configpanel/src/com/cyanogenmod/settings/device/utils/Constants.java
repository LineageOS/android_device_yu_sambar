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

package com.cyanogenmod.settings.device.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Constants {

    public static class GestureCategory {
        public List<GestureSysfs> gestures;
        public boolean defaultValue;

        public GestureCategory(List<GestureSysfs> gestures, boolean defaultValue) {
            this.gestures = gestures;
            this.defaultValue = defaultValue;
        }
    }

    public static class GestureSysfs {
        public String sysfsKey;
        public String valueOn;
        public String valueOff;

        public GestureSysfs(String sysfsKey, String valueOn, String valueOff) {
            this.sysfsKey = sysfsKey;
            this.valueOn = valueOn;
            this.valueOff = valueOff;
        }

        public String getEnableString(boolean enable) {
            return sysfsKey + " " + (enable ? valueOn : valueOff);
        }

        public String getEnableRegex() {
            return sysfsKey + " " + "([a-f0-9A-F]{2})";
        }
    }

    // Preference keys
    private static final String TOUCHSCREEN_CAMERA_GESTURE_KEY = "touchscreen_gesture_camera";
    private static final String TOUCHSCREEN_MUSIC_GESTURE_KEY = "touchscreen_gesture_music";
    private static final String TOUCHSCREEN_FLASHLIGHT_GESTURE_KEY =
            "touchscreen_gesture_flashlight";

    // Proc nodes
    public static final String TOUCHSCREEN_GESTURE_LIST_NODE =
            "/sys/devices/soc.0/f9924000.i2c/i2c-2/2-004a/gesture_list";

    // Holds <preference_key> -> <proc_node> mapping
    public static final Map<String, GestureCategory> sGestureMap =
            new HashMap<String, GestureCategory>();

    static {
        sGestureMap.put(TOUCHSCREEN_CAMERA_GESTURE_KEY,
            new GestureCategory(Arrays.asList(new GestureSysfs("o", "09", "0a")), true));
        sGestureMap.put(TOUCHSCREEN_MUSIC_GESTURE_KEY,
            new GestureCategory(Arrays.asList(
                new GestureSysfs("LEFT", "01", "0a"),
                //TODO: Add support for swipe down in kernel
                /*new GestureSysfs("DOWN", "01", "0a"),*/
                new GestureSysfs("RIGHT", "01", "0a")), true));
        sGestureMap.put(TOUCHSCREEN_FLASHLIGHT_GESTURE_KEY,
            new GestureCategory(Arrays.asList(new GestureSysfs("v", "09", "0a")), true));
    }

    public static boolean isPreferenceEnabled(Context context, String key, boolean defaultValue) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean(key, defaultValue);
    }
}
