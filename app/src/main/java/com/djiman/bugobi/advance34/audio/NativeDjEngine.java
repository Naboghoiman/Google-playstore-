package com.djiman.bugobi.advance34.audio;

import com.djiman.bugobi.advance34.model.PcmTrack;

/** JNI bridge to the AAudio/C++ mixer.. */
public final class NativeDjEngine implements AutoCloseable {
    static { System.loadLibrary("djiman_native"); }

    public static final int DECK_A = 0;
    public static final int DECK_B = 1;
    public static final int CROSSFADER_LINEAR = 0;
    public static final int CROSSFADER_SMOOTH = 1;
    public static final int CROSSFADER_SCRATCH = 2;
    public static final int FX_FLANGER = 0;
    public static final int FX_PHASER = 1;
    public static final int FX_CUT = 2;

    // Master rack modules, matching the supplied 3.4 Master FX artwork.
    public static final int RACK_REVERB = 0;
    public static final int RACK_DELAY = 1;
    public static final int RACK_ECHO = 2;
    public static final int RACK_CHORUS = 3;
    public static final int RACK_FLANGER = 4;
    public static final int RACK_PHASER = 5;
    public static final int RACK_DISTORTION = 6;
    public static final int RACK_COMPRESSOR = 7;
    public static final int RACK_LIMITER = 8;
    public static final int RACK_FILTER = 9;
    public static final int RACK_STEREO_WIDTH = 10;
    public static final int RACK_EQ_COLOR = 11;

    private long handle;

    public NativeDjEngine() {
        handle = nCreate();
        if (handle == 0) throw new IllegalStateException("Native DJ engine could not be created");
    }

    public synchronized void start() { check(); if (!nStart(handle)) throw new IllegalStateException("AAudio stream could not start"); }
    public synchronized void stop() { if (handle != 0) nStop(handle); }

    public void loadDeck(int deck, PcmTrack pcm) { check(); nLoadDeck(handle, deck, pcm.stereo, pcm.sampleRate); }
    public void unloadDeck(int deck) { check(); nUnloadDeck(handle, deck); }
    public void play(int deck) { check(); nSetPlaying(handle, deck, true); }
    /** Starts on the native render clock after the requested delay. */
    public void playScheduled(int deck, double delaySeconds) { check(); nSchedulePlay(handle, deck, Math.max(0, delaySeconds)); }
    public void pause(int deck) { check(); nSetPlaying(handle, deck, false); }
    public boolean isPlaying(int deck) { check(); return nIsPlaying(handle, deck); }
    public void seek(int deck, double seconds) { check(); nSeek(handle, deck, seconds); }
    public double position(int deck) { check(); return nPosition(handle, deck); }
    public double audiblePosition(int deck) { check(); return nAudiblePosition(handle, deck); }
    public double duration(int deck) { check(); return nDuration(handle, deck); }

    public void setPlaybackRate(int deck, double rate) { check(); nSetPlaybackRate(handle, deck, rate); }
    public double playbackRate(int deck) { check(); return nPlaybackRate(handle, deck); }
    public void setPitchBend(int deck, double bend) { check(); nSetPitchBend(handle, deck, bend); }
    public void setPhaseCorrection(int deck, double correction) { check(); nSetPhaseCorrection(handle, deck, correction); }
    public void setKeyLock(int deck, boolean enabled) { check(); nSetKeyLock(handle, deck, enabled); }
    public void setKeySemitones(int deck, double semitones) { check(); nSetKeySemitones(handle, deck, semitones); }

    public void setTrim(int deck, float value) { check(); nSetTrim(handle, deck, value); }
    public void setVolume(int deck, float value) { check(); nSetVolume(handle, deck, value); }
    public void setEq(int deck, float lowDb, float midDb, float highDb) { check(); nSetEq(handle, deck, lowDb, midDb, highDb); }
    public void setFilter(int deck, float value) { check(); nSetFilter(handle, deck, value); }
    public void setLoop(int deck, boolean enabled, double startSec, double endSec) { check(); nSetLoop(handle, deck, enabled, startSec, endSec); }
    public void beginScratch(int deck) { check(); nBeginScratch(handle, deck); }
    public void moveScratch(int deck, double positionSeconds, double rate) { check(); nMoveScratch(handle, deck, positionSeconds, rate); }
    public void endScratch(int deck) { check(); nEndScratch(handle, deck); }

    public void setCrossfader(float value) { check(); nSetCrossfader(handle, value); }
    public float crossfader() { check(); return nCrossfader(handle); }
    public void setCrossfaderCurve(int curve) { check(); nSetCrossfaderCurve(handle, curve); }
    public void setMasterLevel(float value) { check(); nSetMasterLevel(handle, value); }
    public void setMasterTrim(float value) { check(); nSetMasterTrim(handle, value); }
    public void setMasterBalance(float value) { check(); nSetMasterBalance(handle, value); }
    public void setMasterEq(float lowDb, float midDb, float highDb) { check(); nSetMasterEq(handle, lowDb, midDb, highDb); }
    public void setMasterFilter(float value) { check(); nSetMasterFilter(handle, value); }
    public void setMasterFx(int type, boolean enabled, float time, float depth, float level) { check(); nSetMasterFx(handle, type, enabled, time, depth, level); }
    public void setRackFx(int module, boolean enabled, float amount, int mode) { check(); nSetRackFx(handle, module, enabled, amount, mode); }
    public void setRackFxWet(float wet) { check(); nSetRackFxWet(handle, wet); }
    public void setRackFxTempo(float bpm) { check(); nSetRackFxTempo(handle, bpm); }

    public float vuLeft() { check(); return nVuLeft(handle); }
    public float vuRight() { check(); return nVuRight(handle); }
    public float vuDeck(int deck) { check(); return nVuDeck(handle, deck); }

    public void loadSample(int slot, PcmTrack pcm) { check(); nLoadSample(handle, slot, pcm.stereo, pcm.sampleRate); }
    public void clearSample(int slot) { check(); nClearSample(handle, slot); }
    public void triggerSample(int slot, float gain) { check(); nTriggerSample(handle, slot, gain); }
    public void triggerSampleScheduled(int slot, float gain, double delaySeconds) { check(); nTriggerSampleScheduled(handle, slot, gain, Math.max(0, delaySeconds)); }
    public void setPadVolume(float gain) { check(); nSetPadVolume(handle, gain); }

    public void loadLooper(PcmTrack pcm, double bpm) { loadLooperSlot(0, pcm, bpm); }
    public void startLooper(double targetBpm, double startAtSeconds) { startLooperSlot(0, targetBpm, startAtSeconds); }
    public void stopLooper() { stopLooperSlot(0); }
    public void loadLooperSlot(int slot, PcmTrack pcm, double bpm) { check(); nLoadLooperSlot(handle, slot, pcm.stereo, pcm.sampleRate, bpm); }
    public void startLooperSlot(int slot, double targetBpm, double startAtSeconds) { check(); nStartLooperSlot(handle, slot, targetBpm, startAtSeconds); }
    public void stopLooperSlot(int slot) { check(); nStopLooperSlot(handle, slot); }
    public void clearLooperSlot(int slot) { check(); nClearLooperSlot(handle, slot); }
    public boolean isLooperSlotPlaying(int slot) { check(); return nIsLooperSlotPlaying(handle, slot); }
    public void setLooperGain(float gain) { check(); nSetLooperGain(handle, gain); }
    public void setLooperTone(float filter, float bassDb) { check(); nSetLooperTone(handle, filter, bassDb); }


    /** Publishes the analysed source-coordinate beat grid to the render thread. */
    public void setDeckBeatGrid(int deck, double bpm, double offsetSeconds, double[] beatTimes) {
        check(); nSetDeckBeatGrid(handle, deck, bpm, offsetSeconds, beatTimes == null ? new double[0] : beatTimes);
    }

    /** Loads one synthesized live-drum row into the native Rhythm Socket. */
    public void loadRhythmRow(int row, PcmTrack pcm) { check(); nLoadRhythmRow(handle, row, pcm.stereo, pcm.sampleRate); }
    /** rowMasks contains 18 sixteen-bit step masks; enabledMask uses one bit per internal row. */
    public void setRhythmPattern(int[] rowMasks, int enabledMask, float level) { check(); nSetRhythmPattern(handle, rowMasks, enabledMask, level); }
    public void setRhythmActive(boolean active) { check(); nSetRhythmActive(handle, active); }
    public boolean isRhythmActive() { check(); return nRhythmActive(handle); }
    public int rhythmCurrentStep() { check(); return nRhythmCurrentStep(handle); }
    public void setRhythmKey(int semitones) { check(); nSetRhythmKey(handle, Math.max(-12, Math.min(12, semitones))); }
    public void shiftRhythm(int steps) { check(); nShiftRhythm(handle, steps); }

    /** Starts native LAME encoding at 320 kbps. path must be writable by the app. */
    public boolean startRecording(String path) { check(); return nStartRecording(handle, path, 320); }
    public void stopRecording() { check(); nStopRecording(handle); }
    public boolean isRecording() { check(); return nIsRecording(handle); }

    private void check() { if (handle == 0) throw new IllegalStateException("DJ engine is closed"); }

    @Override public synchronized void close() {
        if (handle != 0) { nDestroy(handle); handle = 0; }
    }

    private static native long nCreate();
    private static native boolean nStart(long h);
    private static native void nStop(long h);
    private static native void nDestroy(long h);
    private static native void nLoadDeck(long h, int deck, float[] stereo, int sampleRate);
    private static native void nUnloadDeck(long h, int deck);
    private static native void nSetPlaying(long h, int deck, boolean playing);
    private static native void nSchedulePlay(long h, int deck, double delaySeconds);
    private static native boolean nIsPlaying(long h, int deck);
    private static native void nSeek(long h, int deck, double seconds);
    private static native double nPosition(long h, int deck);
    private static native double nAudiblePosition(long h, int deck);
    private static native double nDuration(long h, int deck);
    private static native void nSetPlaybackRate(long h, int deck, double rate);
    private static native double nPlaybackRate(long h, int deck);
    private static native void nSetPitchBend(long h, int deck, double bend);
    private static native void nSetPhaseCorrection(long h, int deck, double correction);
    private static native void nSetKeyLock(long h, int deck, boolean enabled);
    private static native void nSetKeySemitones(long h, int deck, double semitones);
    private static native void nSetTrim(long h, int deck, float value);
    private static native void nSetVolume(long h, int deck, float value);
    private static native void nSetEq(long h, int deck, float lowDb, float midDb, float highDb);
    private static native void nSetFilter(long h, int deck, float value);
    private static native void nSetLoop(long h, int deck, boolean enabled, double startSec, double endSec);
    private static native void nBeginScratch(long h, int deck);
    private static native void nMoveScratch(long h, int deck, double positionSeconds, double rate);
    private static native void nEndScratch(long h, int deck);
    private static native void nSetCrossfader(long h, float value);
    private static native float nCrossfader(long h);
    private static native void nSetCrossfaderCurve(long h, int curve);
    private static native void nSetMasterLevel(long h, float value);
    private static native void nSetMasterTrim(long h, float value);
    private static native void nSetMasterBalance(long h, float value);
    private static native void nSetMasterEq(long h, float lowDb, float midDb, float highDb);
    private static native void nSetMasterFilter(long h, float value);
    private static native void nSetMasterFx(long h, int type, boolean enabled, float time, float depth, float level);
    private static native void nSetRackFx(long h, int module, boolean enabled, float amount, int mode);
    private static native void nSetRackFxWet(long h, float wet);
    private static native void nSetRackFxTempo(long h, float bpm);
    private static native float nVuLeft(long h);
    private static native float nVuRight(long h);
    private static native float nVuDeck(long h, int deck);
    private static native void nLoadSample(long h, int slot, float[] stereo, int sampleRate);
    private static native void nClearSample(long h, int slot);
    private static native void nTriggerSample(long h, int slot, float gain);
    private static native void nTriggerSampleScheduled(long h, int slot, float gain, double delaySeconds);
    private static native void nSetPadVolume(long h, float gain);
    private static native void nLoadLooperSlot(long h, int slot, float[] stereo, int sampleRate, double bpm);
    private static native void nStartLooperSlot(long h, int slot, double targetBpm, double startAtSeconds);
    private static native void nStopLooperSlot(long h, int slot);
    private static native void nClearLooperSlot(long h, int slot);
    private static native boolean nIsLooperSlotPlaying(long h, int slot);
    private static native void nSetLooperGain(long h, float gain);
    private static native void nSetLooperTone(long h, float filter, float bassDb);

    private static native void nSetDeckBeatGrid(long h, int deck, double bpm, double offsetSeconds, double[] beatTimes);
    private static native void nLoadRhythmRow(long h, int row, float[] stereo, int sampleRate);
    private static native void nSetRhythmPattern(long h, int[] rowMasks, int enabledMask, float level);
    private static native void nSetRhythmActive(long h, boolean active);
    private static native boolean nRhythmActive(long h);
    private static native int nRhythmCurrentStep(long h);
    private static native void nSetRhythmKey(long h, int semitones);
    private static native void nShiftRhythm(long h, int steps);
    private static native boolean nStartRecording(long h, String path, int kbps);
    private static native void nStopRecording(long h);
    private static native boolean nIsRecording(long h);
}
