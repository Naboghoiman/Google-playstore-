package com.djiman.bugobi.advance34.model;

public final class DeckState {
    public final String id;
    public TrackInfo info;
    public PcmTrack pcm;
    public AnalysisResult analysis;
    public boolean loading;
    public boolean playing;
    public boolean syncLocked;
    public boolean keyLock = true;
    public boolean looping;
    public double playbackRate = 1.0;
    public double pitchBend = 0.0;
    public double keySemitones = 0.0;
    public double tempoRangePercent = 8.0;
    public float trim = 1f;
    public float volume = 1f;
    public float eqHighDb = 0f;
    public float eqMidDb = 0f;
    public float eqLowDb = 0f;
    public float filter = 0f;

    public DeckState(String id) { this.id = id; }

    public double bpm() { return analysis == null ? 0 : analysis.bpm; }
    public double beatgridOffset() { return analysis == null ? 0 : analysis.beatgridOffset; }
}
