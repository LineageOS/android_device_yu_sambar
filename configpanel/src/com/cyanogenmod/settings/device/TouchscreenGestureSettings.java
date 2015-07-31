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

import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.SwitchPreference;
import android.util.Log;
import android.view.MenuItem;

import java.util.List;
import java.util.Map;

import com.android.internal.util.cm.ScreenType;

import com.cyanogenmod.settings.device.utils.Constants;
import com.cyanogenmod.settings.device.utils.Constants.GestureCategory;
import com.cyanogenmod.settings.device.utils.Constants.GestureSysfs;

public class TouchscreenGestureSettings extends PreferenceActivity
        implements OnPreferenceChangeListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.touchscreen_panel);
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // If running on a phone, remove padding around the listview
        if (!ScreenType.isTablet(this)) {
            getListView().setPadding(0, 0, 0, 0);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        GestureCategory category = Constants.sGestureMap.get(preference.getKey());
        if (category != null) {
            Boolean value = (Boolean) newValue;
            setCategoryEnable(category, value);
            return true;
        }
        return false;
    }

    @Override
    public void addPreferencesFromResource(int preferencesResId) {
        super.addPreferencesFromResource(preferencesResId);
        // Initialize node preferences
        for (Map.Entry<String, GestureCategory> entry : Constants.sGestureMap.entrySet()) {
            SwitchPreference b = (SwitchPreference) findPreference(entry.getKey());
            if (b == null) continue;
            b.setOnPreferenceChangeListener(this);
            GestureCategory category = entry.getValue();
            List<GestureSysfs> gestures = category.gestures;
            boolean[] isEnabled = new boolean[gestures.size()];

            // Get the state of each gesture within the category
            for (int i = 0; i < gestures.size(); i++) {
                GestureSysfs gesture = gestures.get(i);
                isEnabled[i] = gesture.isEnabled();
            }

            //Ensure they all match.  If one doesn't, disable all.
            boolean first = isEnabled[0];
            for(int i = 1; i < isEnabled.length; i++) {
                if (isEnabled[i] != first) {
                    setCategoryEnable(category, false);
                    b.setChecked(false);
                    return;
                }
            }

            b.setChecked(first);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static void setCategoryEnable(GestureCategory category, boolean enable) {
        for (GestureSysfs sysfs : category.gestures) {
            sysfs.setEnabled(enable);
        }
    }
}
