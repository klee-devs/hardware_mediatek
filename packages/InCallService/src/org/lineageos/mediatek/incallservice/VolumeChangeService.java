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

        if (mAudioManager == null) {
            Log.e(LOG_TAG, "AudioManager unavailable; volume monitoring cannot start");
            return START_STICKY;
        }

        if (mVolumeChangeReceiver == null) {
            VolumeChangeReceiver receiver = new VolumeChangeReceiver(mAudioManager);
            IntentFilter volumeFilter = new IntentFilter(AudioManager.VOLUME_CHANGED_ACTION);
            volumeFilter.addAction(AudioManager.STREAM_DEVICES_CHANGED_ACTION);
            try {
                registerReceiver(receiver, volumeFilter, Context.RECEIVER_NOT_EXPORTED);
                mVolumeChangeReceiver = receiver;
            } catch (SecurityException | IllegalArgumentException e) {
                Log.e(LOG_TAG, "Unable to register volume-change receiver", e);
            }
        }

        if (mPlaybackCallback == null) {
            AudioManager.AudioPlaybackCallback callback =
                    new AudioManager.AudioPlaybackCallback() {
                @Override
                public void onPlaybackConfigChanged(
                        List<AudioPlaybackConfiguration> configs) {
                    SuperVolumeUtils.syncAll(mAudioManager);
                }
            };
            try {
                mAudioManager.registerAudioPlaybackCallback(
                        callback, new Handler(getMainLooper()));
                mPlaybackCallback = callback;
            } catch (IllegalArgumentException e) {
                Log.e(LOG_TAG, "Unable to register audio playback callback", e);
            }
        }

        // Do this before the optional telephony callback so media boost remains
        // initialized if READ_PHONE_STATE is denied by the permission manager.
        SuperVolumeUtils.syncAll(mAudioManager);

        if (mTelephonyManager != null && mCallStateListener == null) {
            CallStateListener listener = new CallStateListener(mAudioManager);
            try {
                mTelephonyManager.registerTelephonyCallback(getMainExecutor(), listener);
                mCallStateListener = listener;
            } catch (SecurityException | IllegalStateException e) {
                Log.w(LOG_TAG,
                        "Call-state monitoring unavailable; media volume boost remains active",
                        e);
            }
        }

        return START_STICKY;
    }
}
