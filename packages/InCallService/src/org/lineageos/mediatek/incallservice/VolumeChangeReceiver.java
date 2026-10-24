/*
 * Copyright (C) 2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.mediatek.incallservice;

import android.content.Intent;
import android.content.Context;
import android.content.BroadcastReceiver;

import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.AudioDeviceInfo;

import android.util.Log;

public class VolumeChangeReceiver extends BroadcastReceiver {
    public static final String LOG_TAG = "MediatekInCallService";

    private AudioManager mAudioManager;

    public VolumeChangeReceiver(AudioManager audioManager) {
        mAudioManager = audioManager;
    }

    private void handleVolumeStateChange(Intent intent) {
        boolean volumeChanged = AudioManager.VOLUME_CHANGED_ACTION.equals(intent.getAction());
        boolean routeChanged = AudioManager.STREAM_DEVICES_CHANGED_ACTION.equals(intent.getAction());
        if (!volumeChanged && !routeChanged) {
            return;
        }
        if (volumeChanged && intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1)
                != AudioManager.STREAM_VOICE_CALL) {
            return;
        }
        if (mAudioManager.getMode() != AudioManager.MODE_IN_CALL) {
            return;
        }

        AudioDeviceInfo callDevice = mAudioManager.getCommunicationDevice();
        if (callDevice == null) {
            return;
        }

        int volumeIndex = volumeChanged
                ? intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_VALUE, -1) : -1;
        if (volumeIndex == -1) {
            volumeIndex = mAudioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
        }

        GainUtils.setGainLevel(callDevice.getType(), volumeIndex, AudioSystem.STREAM_VOICE_CALL);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
            handleVolumeStateChange(intent);
    }
}
