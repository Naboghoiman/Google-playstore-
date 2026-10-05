package com.djiman.bugobi.advance34;

import android.content.Context;
import com.djiman.bugobi.advance34.audio.NativeDjEngine;
import com.example.audio.DjAudioEngine;

/**
 * High-level DJ Console Controller.
 */
public class DjController {
    private final Context context;
    private final DjAudioEngine audioEngine;

    public DjController(Context context) {
        this.context = context;
        this.audioEngine = NativeDjEngine.getInstance(context).getAudioEngine();
    }

    public DjAudioEngine getAudioEngine() {
        return audioEngine;
    }
}
