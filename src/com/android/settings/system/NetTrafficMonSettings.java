/*
 * Copyright (C) 2020 Yet Another AOSP Project
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
package com.android.settings.system;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import androidx.preference.ListPreference;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceCategory;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragment;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.widget.MainSwitchPreference;

import com.yasp.settings.preferences.CustomSeekBarPreference;
import com.yasp.settings.preferences.SystemSettingSwitchPreference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SearchIndexable
public class NetTrafficMonSettings extends DashboardFragment implements
        OnPreferenceChangeListener {

    private static final String TAG = "NetTrafficMonSettings";
    private static final String KEY_MASTER = "network_traffic_state";
    private static final String NETWORK_TRAFFIC_FONT_SIZE  = "network_traffic_font_size";
    private static final String NETWORK_TRAFFIC_LOCATION = "network_traffic_location";
    private static final String NETWORK_TRAFFIC_LIMIT_MB = "network_traffic_limit_mb";
    private static final String NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD = "network_traffic_autohide_threshold";
    private static final String NETWORK_TRAFFIC_ARROW = "network_traffic_arrow";

    private MainSwitchPreference mMasterSwitch;
    private ListPreference mNetTrafficLocation;
    private SwitchPreferenceCompat mThresholdMb;
    private CustomSeekBarPreference mThreshold;
    private SystemSettingSwitchPreference mShowArrows;
    private ListPreference mNetTrafficType;
    private CustomSeekBarPreference mNetTrafficSize;

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.yaap_settings_net_traffic;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final ContentResolver resolver = getContentResolver();

        mMasterSwitch = findPreference(KEY_MASTER);
        boolean enabled = Settings.System.getInt(resolver,
                KEY_MASTER, 0) == 1;
        mMasterSwitch.setChecked(enabled);
        mMasterSwitch.addOnSwitchChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Settings.System.putInt(getContentResolver(),
                        KEY_MASTER, isChecked ? 1 : 0);
                updateMasterEnablement(isChecked);
            }
        });
        updateMasterEnablement();

        int type = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_TYPE, 0, UserHandle.USER_CURRENT);
        mNetTrafficType = (ListPreference) findPreference("network_traffic_type");
        mNetTrafficType.setValue(String.valueOf(type));
        mNetTrafficType.setSummary(mNetTrafficType.getEntry());
        mNetTrafficType.setOnPreferenceChangeListener(this);

        int NetTrafficSize = Settings.System.getInt(resolver,
                Settings.System.NETWORK_TRAFFIC_FONT_SIZE, 36);
        mNetTrafficSize = (CustomSeekBarPreference) findPreference(NETWORK_TRAFFIC_FONT_SIZE);
        mNetTrafficSize.setValue(NetTrafficSize);
        mNetTrafficSize.setOnPreferenceChangeListener(this);

        mNetTrafficLocation = (ListPreference) findPreference(NETWORK_TRAFFIC_LOCATION);
        int location = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_VIEW_LOCATION, 0, UserHandle.USER_CURRENT);
        mNetTrafficLocation.setOnPreferenceChangeListener(this);
        mNetTrafficLocation.setValue(String.valueOf(location));
        mNetTrafficLocation.setSummary(mNetTrafficLocation.getEntry());

        mThreshold = (CustomSeekBarPreference) findPreference(NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD);
        int value = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD, 1, UserHandle.USER_CURRENT);
        boolean isMB = value > mThreshold.getMax();
        if (isMB) {
            value /= 1000;
            mThreshold.setUnits(getString(R.string.unit_mbps));
        }
        mThreshold.setValue(value);
        mThreshold.setOnPreferenceChangeListener(this);

        mThresholdMb = (SwitchPreferenceCompat) findPreference(NETWORK_TRAFFIC_LIMIT_MB);
        mThresholdMb.setChecked(isMB);
        mThresholdMb.setOnPreferenceChangeListener(this);

        mShowArrows = (SystemSettingSwitchPreference) findPreference(NETWORK_TRAFFIC_ARROW);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == mNetTrafficLocation) {
            int location = Integer.valueOf((String) objValue);
            // 0=sb; 1=expanded sb; 2 = both
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.NETWORK_TRAFFIC_VIEW_LOCATION, location, UserHandle.USER_CURRENT);
            int index = mNetTrafficLocation.findIndexOfValue((String) objValue);
            mNetTrafficLocation.setSummary(mNetTrafficLocation.getEntries()[index]);
            mNetTrafficLocation.setValue(String.valueOf(location));
            return true;
        } else if (preference == mThreshold) {
            int val = (Integer) objValue;
            if (mThresholdMb.isChecked()) val *= 1000;
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD, val,
                    UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mThresholdMb) {
            boolean val = (Boolean) objValue;
            setLimitInMb(val);
            return true;
        } else if (preference == mNetTrafficType) {
            int val = Integer.valueOf((String) objValue);
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.NETWORK_TRAFFIC_TYPE, val,
                    UserHandle.USER_CURRENT);
            int index = mNetTrafficType.findIndexOfValue((String) objValue);
            mNetTrafficType.setSummary(mNetTrafficType.getEntries()[index]);
            return true;
        } else if (preference == mNetTrafficSize) {
            int width = (Integer) objValue;
            Settings.System.putInt(getContentResolver(),
                    Settings.System.NETWORK_TRAFFIC_FONT_SIZE, width);
            return true;
        }
        return false;
    }

    private void updateMasterEnablement() {
        final boolean enabled = Settings.System.getInt(
                getContentResolver(), KEY_MASTER, 0) == 1;
        updateMasterEnablement(enabled);
    }

    private void updateMasterEnablement(boolean enabled) {
        final PreferenceScreen screen = getPreferenceScreen();
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            Preference pref = screen.getPreference(i);
            if (KEY_MASTER.equals(pref.getKey()))
                continue;
            pref.setEnabled(enabled);
        }
    }

    private void setLimitInMb(boolean value) {
        mThreshold.setUnits(getString(value ?
                R.string.unit_mbps : R.string.unit_kbps));
        int limit = mThreshold.getValue();
        if (value) limit *= 1000;
        Settings.System.putIntForUser(getContentResolver(),
                Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD, limit,
                UserHandle.USER_CURRENT);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.YASP;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.yaap_settings_net_traffic);
}
