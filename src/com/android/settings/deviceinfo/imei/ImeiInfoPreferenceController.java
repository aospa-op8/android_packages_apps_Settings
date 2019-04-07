/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.settings.deviceinfo.imei;

import static android.telephony.TelephonyManager.PHONE_TYPE_CDMA;

import android.content.Context;
import android.os.UserManager;
import android.telephony.TelephonyManager;

import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.slices.Sliceable;
import com.android.settings.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller that manages preference for single and multi sim devices.
 */
public class ImeiInfoPreferenceController extends BasePreferenceController {

    private final boolean mIsMultiSim;
    private final TelephonyManager mTelephonyManager;
    private final List<Preference> mPreferenceList = new ArrayList<>();
    private Fragment mFragment;

    public ImeiInfoPreferenceController(Context context, String key) {
        super(context, key);
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mIsMultiSim = mTelephonyManager.getPhoneCount() > 1;
    }

    public void setHost(Fragment fragment) {
        mFragment = fragment;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        final Preference preference = screen.findPreference(getPreferenceKey());

        mPreferenceList.add(preference);
        updatePreference(preference, 0 /* simSlot */);

        final int imeiPreferenceOrder = preference.getOrder();
        // Add additional preferences for each sim in the device
        for (int simSlotNumber = 1; simSlotNumber < mTelephonyManager.getPhoneCount();
                simSlotNumber++) {
            final Preference multiSimPreference = createNewPreference(screen.getContext());
            multiSimPreference.setOrder(imeiPreferenceOrder + simSlotNumber);
            multiSimPreference.setKey(getPreferenceKey() + simSlotNumber);
            screen.addPreference(multiSimPreference);
            mPreferenceList.add(multiSimPreference);
            updatePreference(multiSimPreference, simSlotNumber);
        }

        final int phoneCount = mTelephonyManager.getPhoneCount();
        if (Utils.isSupportCTPA(mContext) && phoneCount >= 2) {
            final int slot0PhoneType = mTelephonyManager.getCurrentPhoneTypeForSlot(0);
            final int slot1PhoneType = mTelephonyManager.getCurrentPhoneTypeForSlot(1);
            if (PHONE_TYPE_CDMA != slot0PhoneType && PHONE_TYPE_CDMA != slot1PhoneType) {
                addPreference(screen, 0, imeiPreferenceOrder + phoneCount,
                        getPreferenceKey() + phoneCount, true);
            } else if (PHONE_TYPE_CDMA == slot0PhoneType){
                addPreference(screen, 0, imeiPreferenceOrder + phoneCount,
                        getPreferenceKey() + phoneCount, false);
            } else if (PHONE_TYPE_CDMA == slot1PhoneType) {
                addPreference(screen, 1, imeiPreferenceOrder + phoneCount,
                        getPreferenceKey() + phoneCount, false);
            }
        }
    }

    private void addPreference(PreferenceScreen screen, int slotNumber, int order,
                               String key, boolean isCDMAPhone) {
        final Preference multiSimPreference = createNewPreference(screen.getContext());
        multiSimPreference.setOrder(order);
        multiSimPreference.setKey(key);
        screen.addPreference(multiSimPreference);
        mPreferenceList.add(multiSimPreference);
        if (isCDMAPhone) {
            multiSimPreference.setTitle(getTitleForCdmaPhone(slotNumber));
            multiSimPreference.setSummary(getMeid(slotNumber));
        } else {
            multiSimPreference.setTitle(getTitleForGsmPhone(slotNumber));
            multiSimPreference.setSummary(mTelephonyManager.getImei(slotNumber));
        }
    }

    @Override
    public CharSequence getSummary() {
        final int phoneType = mTelephonyManager.getPhoneType();
        return phoneType == PHONE_TYPE_CDMA ? mTelephonyManager.getMeid()
                : mTelephonyManager.getImei();
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        final int simSlot = mPreferenceList.indexOf(preference);
        if (simSlot == -1) {
            return false;
        }

        if (Utils.isSupportCTPA(mContext)) {
            return true;
        }

        ImeiInfoDialogFragment.show(mFragment, simSlot, preference.getTitle().toString());
        return true;
    }

    @Override
    public int getAvailabilityStatus() {
        return mContext.getSystemService(UserManager.class).isAdminUser()
                && !Utils.isWifiOnly(mContext) ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public boolean isSliceable() {
        return true;
    }

    @Override
    public boolean isCopyableSlice() {
        return true;
    }

    @Override
    public void copy() {
        Sliceable.setCopyContent(mContext, getSummary(), mContext.getText(R.string.status_imei));
    }

    private void updatePreference(Preference preference, int simSlot) {
        int phoneType = mTelephonyManager.getPhoneType();
        if (Utils.isSupportCTPA(mContext)) {
            phoneType = mTelephonyManager.getCurrentPhoneTypeForSlot(simSlot);
            if (PHONE_TYPE_CDMA == phoneType) {
                simSlot = 0;
            }
        }
        if (phoneType == PHONE_TYPE_CDMA) {
            preference.setTitle(getTitleForCdmaPhone(simSlot));
            preference.setSummary(getMeid(simSlot));
        } else {
            // GSM phone
            preference.setTitle(getTitleForGsmPhone(simSlot));
            preference.setSummary(mTelephonyManager.getImei(simSlot));
        }
    }

    private CharSequence getTitleForGsmPhone(int simSlot) {
        return mIsMultiSim ? mContext.getString(R.string.imei_multi_sim, simSlot + 1)
                : mContext.getString(R.string.status_imei);
    }

    private CharSequence getTitleForCdmaPhone(int simSlot) {
        return mIsMultiSim ? mContext.getString(R.string.meid_multi_sim, simSlot + 1)
                : mContext.getString(R.string.status_meid_number);
    }

    @VisibleForTesting
    String getMeid(int simSlot) {
        return mTelephonyManager.getMeid(simSlot);
    }

    @VisibleForTesting
    Preference createNewPreference(Context context) {
        return new Preference(context);
    }
}
