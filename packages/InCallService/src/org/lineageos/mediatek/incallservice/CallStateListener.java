/*
 * Copyright (C) 2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.mediatek.incallservice;

import android.media.AudioManager;
import android.media.AudioDeviceInfo;
import android.media.AudioSystem;

import android.telephony.TelephonyManager;
import android.telephony.TelephonyCallback;

import android.util.Log;

public class CallStateListener extends TelephonyCallback implements TelephonyCallback.CallStateListener {
    private static final String LOG_TAG = "MtkInCallService";
    private AudioManager mAudioManager;

    public CallStateListener(AudioManager audioManager) {
        mAudioManager = audioManager;
    }

    @Override
    public void onCallStateChanged(int state) {
        if (state == TelephonyManager.CALL_STATE_OFFHOOK || state == TelephonyManager.CALL_STATE_RINGING) {
            Log.d(LOG_TAG, "CallStateListener: call active, setting gain.");
            int volumeIndex = mAudioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
            GainUtils.setGainLevel(volumeIndex);

            AudioDeviceInfo callDevice = mAudioManager.getCommunicationDevice();
            if (state == TelephonyManager.CALL_STATE_OFFHOOK
                    && mAudioManager.getMode() == AudioManager.MODE_IN_CALL
                    && callDevice != null) {
                GainUtils.updateCallVolumeBoost(callDevice.getType(), volumeIndex,
                        AudioSystem.STREAM_VOICE_CALL);
            }
        } else if (state == TelephonyManager.CALL_STATE_IDLE) {
            GainUtils.clearCallVolumeBoost();
        }
    }
}
