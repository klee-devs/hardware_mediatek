/*
 * Copyright (C) 2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.mediatek.incallservice;

import android.media.AudioSystem;
import android.media.AudioDeviceInfo;
import android.os.SystemProperties;
import android.util.Log;

public class GainUtils {
    public static final String LOG_TAG = "MtkInCallService";
    public static final int volSteps = SystemProperties.getInt("ro.config.vc_call_vol_steps", 7);

    /**
     * Sets the gain level for a given audio device.
     * @param audioDevice The audio device to set the gain level for.
     * @param gainIndex The gain level to set.
     * @param streamType The stream type to set the gain level for.
     */
    private static final String CALL_VOLUME_BOOST_SUPPORT =
            "ro.vendor.audio.volume.boost.supportMtk";
    private static final int CALL_BOOST_HANDSET = 1;
    private static final int CALL_BOOST_SPEAKER = 2;

    public static void setGainLevel(int audioDevice, int gainIndex, int streamType) {
        setGainLevel(audioDevice, gainIndex, streamType, true);
    }

    private static void setGainLevel(int audioDevice, int gainIndex, int streamType,
                                     boolean updateCallBoost) {
        String parameters = String.format("volumeDevice=%d;volumeIndex=%d;volumeStreamType=%d",
                                          audioDevice, Math.min(volSteps, gainIndex), streamType);
        Log.d(LOG_TAG, "Setting audio parameters to: " + parameters);
        AudioSystem.setParameters(parameters);
        if (updateCallBoost) {
            updateCallVolumeBoost(audioDevice, gainIndex, streamType);
        }
    }

    /**
     * Mirrors HyperOS's MediaTek call boost: enable the DSP path only at the
     * normal maximum call-volume step, and clear it as soon as the user lowers
     * the volume or changes to an unsupported output route.
     */
    public static void updateCallVolumeBoost(int audioDevice, int gainIndex, int streamType) {
        if (streamType != AudioSystem.STREAM_VOICE_CALL) {
            return;
        }

        int support = SystemProperties.getInt(CALL_VOLUME_BOOST_SUPPORT, 0);
        if (support == 0) {
            return;
        }

        boolean supportedRoute =
                (audioDevice == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                        && (support & CALL_BOOST_HANDSET) != 0)
                || (audioDevice == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                        && (support & CALL_BOOST_SPEAKER) != 0);
        boolean enabled = supportedRoute && gainIndex >= volSteps;
        String parameter = enabled ? "CustInfo=super_voice" : "CustInfo=false";
        AudioSystem.setParameters(parameter);
        Log.i(LOG_TAG, "Call DSP boost " + (enabled ? "enabled" : "cleared")
                + " for device " + audioDevice + " at volume " + gainIndex + "/" + volSteps);
    }

    public static void clearCallVolumeBoost() {
        if (SystemProperties.getInt(CALL_VOLUME_BOOST_SUPPORT, 0) != 0) {
            AudioSystem.setParameters("CustInfo=false");
        }
    }

    /**
     * Sets the gain level for built-in earpiece and bluetooth SCO devices.
     * @param gainIndex The gain level to set.
     */
    public static void setGainLevel(int gainIndex) {
        setGainLevel(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, gainIndex,
                AudioSystem.STREAM_VOICE_CALL, false);
        setGainLevel(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, gainIndex,
                AudioSystem.STREAM_VOICE_CALL, false);
    }
}
