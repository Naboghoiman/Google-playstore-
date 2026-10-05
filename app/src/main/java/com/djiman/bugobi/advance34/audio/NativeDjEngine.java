package com.djiman.bugobi.advance34.audio;

import android.content.Context;
import com.example.audio.DjAudioEngine;

/**
 * Native DJ Engine wrapper and high-performance audio engine provider.
 */
public class NativeDjEngine {
    private static NativeDjEngine instance;
    private DjAudioEngine audioEngine;

    public static synchronized NativeDjEngine getInstance(Context context) {
        if (instance == null) {
            instance = new NativeDjEngine(context.getApplicationContext());
        }
        return instance;
    }

    private NativeDjEngine(Context context) {
        this.audioEngine = new DjAudioEngine(context);
        this.audioEngine.start();
    }

    public DjAudioEngine getAudioEngine() {
        return audioEngine;
    }

    public void start() {
        if (audioEngine != null) {
            audioEngine.start();
        }
    }

    public void stop() {
        if (audioEngine != null) {
            audioEngine.stop();
        }
    }
}
