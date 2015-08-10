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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        private static final String TOUCHSCREEN_GESTURE_LIST_NODE =
                "/sys/devices/soc.0/f9924000.i2c/i2c-2/2-004a/gesture_list";
        private static final String TOUCHSCREEN_GESTURE_EN_NODE =
                "/sys/devices/soc.0/f9924000.i2c/i2c-2/2-004a/en_gesture";

        private static final int ON_BITMASK = 0x01;
        private static final int OFF_BITMASK = 0x02;
        private static final String ENABLE_REGEX = " " + "([a-f0-9A-F]{2})";

        private String sysfsKey;

        public GestureSysfs(String sysfsKey) {
            this.sysfsKey = sysfsKey;
        }

        private String getEnableString(boolean enable) {
            return sysfsKey + " " + (enable ? ON_BITMASK : OFF_BITMASK) + ";";
        }

        private String getEnableRegex() {
            return sysfsKey + ENABLE_REGEX;
        }

        public boolean isEnabled() {
            String currentValue = FileUtils.readOneLine(TOUCHSCREEN_GESTURE_LIST_NODE);
            if (currentValue == null)
                return false;

            Pattern keyValuePattern = Pattern.compile(getEnableRegex());
            Matcher keyValueMatcher = keyValuePattern.matcher(currentValue);
            if (keyValueMatcher.find()) {
                int value = OFF_BITMASK;
                try {
                    value = Integer.parseInt(keyValueMatcher.group(1), 16);
                } catch (NumberFormatException e) {
                    //Ignore
                }
                return (value & ON_BITMASK) == ON_BITMASK;
            }
            return false;
        }

        public void setEnabled(boolean enable) {
            if (enable) {
                enableGestures();
            }
            // Writing is implemented in the driver as read/modify/write
            FileUtils.writeLine(TOUCHSCREEN_GESTURE_LIST_NODE,
                    getEnableString(enable));
            if (!enable) {
                disableGesturesIfAllOff();
            }
        }

        private static void enableGestures() {
            FileUtils.writeLine(TOUCHSCREEN_GESTURE_EN_NODE, "1");
        }

        private static void disableGesturesIfAllOff() {
            String currentVal = FileUtils.readOneLine(TOUCHSCREEN_GESTURE_LIST_NODE);
            if (currentVal == null) {
                return;
            }
            Pattern p = Pattern.compile(ENABLE_REGEX);
            Matcher matcher = p.matcher(currentVal);
            while (matcher.find()) {
                int value = OFF_BITMASK;
                try {
                    value = Integer.parseInt(matcher.group(1), 16);
                } catch (NumberFormatException e) {
                    //Ignore
                }
                if ((value & OFF_BITMASK) != OFF_BITMASK) {
                    return;
                }
            }
            FileUtils.writeLine(TOUCHSCREEN_GESTURE_EN_NODE, "0");
        }

        static boolean hasTouchscreenGestures() {
            return FileUtils.readOneLine(TOUCHSCREEN_GESTURE_LIST_NODE) != null;
        }
    }

    // Preference keys
    private static final String TOUCHSCREEN_CAMERA_GESTURE_KEY = "touchscreen_gesture_camera";
    private static final String TOUCHSCREEN_MUSIC_GESTURE_KEY = "touchscreen_gesture_music";
    private static final String TOUCHSCREEN_FLASHLIGHT_GESTURE_KEY =
            "touchscreen_gesture_flashlight";

    // Holds <preference_key> -> <proc_node> mapping
    public static final Map<String, GestureCategory> sGestureMap =
            new HashMap<String, GestureCategory>();

    static {
        sGestureMap.put(TOUCHSCREEN_CAMERA_GESTURE_KEY,
            new GestureCategory(Arrays.asList(new GestureSysfs("o")), true));
        sGestureMap.put(TOUCHSCREEN_MUSIC_GESTURE_KEY,
            new GestureCategory(Arrays.asList(
                new GestureSysfs("LEFT"),
                new GestureSysfs("DOWN"),
                new GestureSysfs("RIGHT")), true));
        sGestureMap.put(TOUCHSCREEN_FLASHLIGHT_GESTURE_KEY,
            new GestureCategory(Arrays.asList(new GestureSysfs("v")), true));
    }

    public static boolean isPreferenceEnabled(Context context, String key, boolean defaultValue) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean(key, defaultValue);
    }

    public static boolean hasTouchscreenGestures() {
        return GestureSysfs.hasTouchscreenGestures();
    }
}
