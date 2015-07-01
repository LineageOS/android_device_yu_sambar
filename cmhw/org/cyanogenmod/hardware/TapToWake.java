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
    private static final String GESTURE = "TAP";
    private static final String REGEX = GESTURE + " ([a-f0-9A-F]{2})";
    private static final String ON = "01";
    private static final String OFF = "0a";
    private static final Pattern PATTERN = Pattern.compile(REGEX);

    private static String getCurrentValue() {
        String currentVal = FileUtils.readOneLine(CONTROL_PATH);
        if (currentVal == null) {
            return null;
        }
        Matcher matcher = PATTERN.matcher(currentVal);
        return matcher.group(1);
    }

    public static boolean isSupported() {
        return getCurrentValue() != null;
    }

    public static boolean isEnabled()  {
        String currentValue = getCurrentValue();
        return ON.equalsIgnoreCase(currentValue);
    }

    public static boolean setEnabled(boolean state)  {
        return FileUtils.writeLine(CONTROL_PATH, GESTURE + " " + (state ? ON : OFF));
    }
}
