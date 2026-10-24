/*
 * Copyright (C) 2026 The Klee Developers
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.mediatek.incallservice;

import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.os.SystemProperties;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies MediaTek's SuperVolume DSP scene when an enabled stream reaches its
 * normal maximum on the built-in speaker.
 */
public final class SuperVolumeUtils {
    private static final String LOG_TAG = "MtkInCallService";
    private static final String SUPPORT_PROPERTY =
            "ro.vendor.audio.supervolume.support";
    private static final String INDEX_ADD_PROPERTY =
            "ro.vendor.audio.volume_super_index_add";
    private static final String STREAM_TYPE_PROPERTY =
            "ro.vendor.audio.volume_super_streamtype";

    private static final boolean SUPPORTED =
            SystemProperties.getInt(SUPPORT_PROPERTY, 0) != 0;
    private static final int INDEX_ADD =
            SystemProperties.getInt(INDEX_ADD_PROPERTY, 0);
    private static final int STREAM_TYPE_MASK =
            SystemProperties.getInt(STREAM_TYPE_PROPERTY, 0);

    private static final int[] STREAM_TYPES = {
        AudioSystem.STREAM_RING,
        AudioSystem.STREAM_MUSIC,
        AudioSystem.STREAM_NOTIFICATION
    };

    private static final Object LOCK = new Object();
    private static final Map<Integer, String> LAST_PARAMETERS = new HashMap<>();

    private SuperVolumeUtils() {}

    public static boolean supportsStream(int streamType) {
        if (!SUPPORTED || INDEX_ADD <= 0 || streamType < 0 || streamType > 30) {
            return false;
        }
        return (STREAM_TYPE_MASK & (1 << streamType)) != 0;
    }

    public static void update(AudioManager audioManager, int streamType, int volumeIndex) {
        if (audioManager == null || !supportsStream(streamType)) {
            return;
        }

        int maxVolume = audioManager.getStreamMaxVolume(streamType);
        if (maxVolume <= 0) {
            return;
        }

        int currentVolume = volumeIndex < 0
                ? audioManager.getStreamVolume(streamType)
                : volumeIndex;
        currentVolume = Math.max(0, Math.min(currentVolume, maxVolume));

        int superGrade = INDEX_ADD / 10;
        // The HAL uses 15 as the neutral base; higher indices select a DSP scene.
        int normalizedVolume = (currentVolume * 15 + maxVolume / 2) / maxVolume;
        boolean enable = currentVolume == maxVolume
                && superGrade > 0
                && isBuiltInSpeakerRoute(audioManager, streamType);

        int speakerVolumeIndex = enable ? 15 + superGrade : normalizedVolume;
        String parameters = "SpkVolIdx=" + speakerVolumeIndex
                + ";SuperStream=" + streamType;

        synchronized (LOCK) {
            if (parameters.equals(LAST_PARAMETERS.get(streamType))) {
                return;
            }
            int status = AudioSystem.setParameters(parameters);
            if (status == 0) {
                LAST_PARAMETERS.put(streamType, parameters);
                Log.i(LOG_TAG, "Media SuperVolume " + (enable ? "enabled" : "cleared")
                        + " for stream " + streamType + " at " + currentVolume + "/" + maxVolume);
            } else {
                Log.w(LOG_TAG, "Media SuperVolume parameter failed for stream "
                        + streamType + ": " + status);
            }
        }
    }

    public static void syncAll(AudioManager audioManager) {
        for (int streamType : STREAM_TYPES) {
            update(audioManager, streamType, -1);
        }
    }

    public static void syncMusic(AudioManager audioManager) {
        update(audioManager, AudioSystem.STREAM_MUSIC, -1);
    }

    private static boolean isBuiltInSpeakerRoute(AudioManager audioManager, int streamType) {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setLegacyStreamType(streamType)
                .build();
        List<AudioDeviceInfo> devices = audioManager.getAudioDevicesForAttributes(attributes);
        return devices.size() == 1
                && devices.get(0).getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
    }
}
