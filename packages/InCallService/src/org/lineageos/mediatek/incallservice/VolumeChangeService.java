/*
 * Copyright (C) 2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.mediatek.incallservice;

import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;

import android.telephony.TelephonyManager;
import android.telephony.TelephonyCallback;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.app.Service;
import android.os.IBinder;
import android.os.Handler;

import android.util.Log;

import java.util.List;

public class VolumeChangeService extends Service {
    public static final String LOG_TAG = "MediatekInCallService";

    private Context mContext;
    private AudioManager mAudioManager;
    private TelephonyManager mTelephonyManager;
    private VolumeChangeReceiver mVolumeChangeReceiver;
    private CallStateListener mCallStateListener;
    private AudioManager.AudioPlaybackCallback mPlaybackCallback;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (mAudioManager != null && mPlaybackCallback != null) {
            mAudioManager.unregisterAudioPlaybackCallback(mPlaybackCallback);
            mPlaybackCallback = null;
        }
        if (mVolumeChangeReceiver != null) {
            unregisterReceiver(mVolumeChangeReceiver);
            mVolumeChangeReceiver = null;
        }
        if (mTelephonyManager != null && mCallStateListener != null) {
            mTelephonyManager.unregisterTelephonyCallback(mCallStateListener);
            mCallStateListener = null;
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startid) {
        mContext = this;

        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mTelephonyManager =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);

        Log.i(LOG_TAG, "Service is starting...");

        if (mVolumeChangeReceiver == null) {
            mVolumeChangeReceiver = new VolumeChangeReceiver(mAudioManager);
            mCallStateListener = new CallStateListener(mAudioManager);

            IntentFilter volumeFilter = new IntentFilter(AudioManager.VOLUME_CHANGED_ACTION);
            volumeFilter.addAction(AudioManager.STREAM_DEVICES_CHANGED_ACTION);
            registerReceiver(mVolumeChangeReceiver, volumeFilter);
            mTelephonyManager.registerTelephonyCallback(getMainExecutor(), mCallStateListener);

            mPlaybackCallback = new AudioManager.AudioPlaybackCallback() {
                @Override
                public void onPlaybackConfigChanged(
                        List<AudioPlaybackConfiguration> configs) {
                    if (mVolumeChangeReceiver != null) {
                        mVolumeChangeReceiver.syncMusicSuperVolume();
                    }
                }
            };
            mAudioManager.registerAudioPlaybackCallback(
                    mPlaybackCallback, new Handler(getMainLooper()));
        }

        mVolumeChangeReceiver.syncSuperVolumes();

        return START_STICKY;
    }
}
