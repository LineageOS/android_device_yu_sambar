/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package org.cyanogenmod.hardware;

import org.cyanogenmod.hardware.util.FileUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TapToWake {

    private static final String CONTROL_PATH =
            "/sys/devices/soc.0/f9924000.i2c/i2c-2/2-004a/gesture_list";
    private static final String CONTROL_EN_PATH =
            "/sys/devices/soc.0/f9924000.i2c/i2c-2/2-004a/en_gesture";
    private static final String GESTURE = "TAP";
    private static final String HEX_BYTE_GROUP = "([a-f0-9A-F]{2})";
    private static final String REGEX = GESTURE + " " + HEX_BYTE_GROUP;
    private static final int ON_BITMASK = 0x01;
    private static final int OFF_BITMASK = 0x02;
    private static final Pattern PATTERN = Pattern.compile(REGEX);
    private static final Pattern PATTERN_EN = Pattern.compile(" " + HEX_BYTE_GROUP);

    private static String getCurrentValue() {
        String currentVal = FileUtils.readOneLine(CONTROL_PATH);
        if (currentVal == null) {
            return null;
        }
        Matcher matcher = PATTERN.matcher(currentVal);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }

    private static void enableGestures() {
        FileUtils.writeLine(CONTROL_EN_PATH, "1");
    }

    private static void disableGesturesIfAllOff() {
        String currentVal = FileUtils.readOneLine(CONTROL_PATH);
        if (currentVal == null) {
            return;
        }
        Matcher matcher = PATTERN_EN.matcher(currentVal);
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
        FileUtils.writeLine(CONTROL_EN_PATH, "0");
    }

    public static boolean isSupported() {
        return getCurrentValue() != null;
    }

    public static boolean isEnabled()  {
        String currentValue = getCurrentValue();
        if (currentValue == null)
            return false;

        int value = OFF_BITMASK;
        try {
            value = Integer.parseInt(currentValue, 16);
        } catch (NumberFormatException e) {
            //Ignore
        }
        return (value & ON_BITMASK) == ON_BITMASK;
    }

    public static boolean setEnabled(boolean state)  {
        boolean result = false;
        if (state) {
            enableGestures();
        }
        // Writing is implemented in the driver as read/modify/write
        result = FileUtils.writeLine(CONTROL_PATH, GESTURE + " " + (state ? ON_BITMASK : OFF_BITMASK) + ";");
        if (!state) {
            disableGesturesIfAllOff();
        }
        return result;
    }
}
