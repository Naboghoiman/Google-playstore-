package com.djiman.bugobi.advance34.model;

public final class PcmTrack {
    public final float[] stereo;
    public final int sampleRate;
    public final long frames;
    public final double durationSeconds;

    public PcmTrack(float[] stereo, int sampleRate) {
        this.stereo = stereo;
        this.sampleRate = sampleRate;
        this.frames = stereo.length / 2L;
        this.durationSeconds = frames / (double) sampleRate;
    }
}
