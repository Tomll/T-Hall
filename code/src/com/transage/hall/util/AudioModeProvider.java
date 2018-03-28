package com.transage.hall.util;

import android.telecom.CallAudioState;

import java.util.ArrayList;
import java.util.List;

/**
 * Proxy class for getting and setting the audio mode.
 */
public class AudioModeProvider {

    static final int AUDIO_MODE_INVALID = 0;

    private static AudioModeProvider sAudioModeProvider = new AudioModeProvider();
    private int mAudioMode = CallAudioState.ROUTE_EARPIECE;
    private boolean mMuted = false;
    private int mSupportedModes = CallAudioState.ROUTE_EARPIECE
            | CallAudioState.ROUTE_BLUETOOTH | CallAudioState.ROUTE_WIRED_HEADSET
            | CallAudioState.ROUTE_SPEAKER;
    private final List<AudioModeListener> mListeners = new ArrayList();

    public static AudioModeProvider getInstance() {
        return sAudioModeProvider;
    }

    public void onAudioStateChanged(boolean isMuted, int route, int supportedRouteMask) {
        onAudioModeChange(route, isMuted);
        onSupportedAudioModeChange(supportedRouteMask);
    }

    public void onAudioModeChange(int newMode, boolean muted) {
        if (mAudioMode != newMode) {
            mAudioMode = newMode;
            for (AudioModeListener l : mListeners) {
                l.onAudioMode(mAudioMode);
            }
        }

        if (mMuted != muted) {
            mMuted = muted;
            for (AudioModeListener l : mListeners) {
                l.onMute(mMuted);
            }
        }
    }

    public void onSupportedAudioModeChange(int newModeMask) {
        /// M: For ALPS01825524 @{
        // when mSupportedModes is really changed, then do update
        if (mSupportedModes != newModeMask) {
            /// @}
            mSupportedModes = newModeMask;

            for (AudioModeListener l : mListeners) {
                l.onSupportedAudioMode(mSupportedModes);
            }
        }
    }

    public void addListener(AudioModeListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
            listener.onSupportedAudioMode(mSupportedModes);
            listener.onAudioMode(mAudioMode);
            listener.onMute(mMuted);
        }
    }

    public void removeListener(AudioModeListener listener) {
        if (mListeners.contains(listener)) {
            mListeners.remove(listener);
        }
    }

    public int getSupportedModes() {
        return mSupportedModes;
    }

    public int getAudioMode() {
        return mAudioMode;
    }

    public boolean getMute() {
        return mMuted;
    }

    /* package */ interface AudioModeListener {
        void onAudioMode(int newMode);

        void onMute(boolean muted);

        void onSupportedAudioMode(int modeMask);
    }
}