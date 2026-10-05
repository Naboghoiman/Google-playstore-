package com.djiman.bugobi.advance34.audio;

import com.djiman.bugobi.advance34.model.AnalysisResult;
import com.djiman.bugobi.advance34.analysis.BeatGridMath;
import com.djiman.bugobi.advance34.model.DeckState;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Native replacement for sync-engine.js. It keeps the supplied APK's dynamic-master,
 * half/double tempo-family selection, kick-to-kick start and continuous bounded phase lock.
 */
public final class SyncController implements AutoCloseable {
    public enum Mode { TEMPO, BEAT, BAR }
    private static final double[] FAMILY = {1.0, 0.5, 2.0};
    private static final double LOCKED_MS = 6.0;
    private static final double MAX_PHASE_CORRECTION = 0.020; // ±2%, same bounded micro-correction policy

    private final NativeDjEngine engine;
    private final DeckState a;
    private final DeckState b;
    private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "djiman-sync-clock"); t.setDaemon(true); return t;
    });
    private volatile Mode mode = Mode.BEAT;
    private volatile Snapshot snapshot = new Snapshot("A", 0, 0, false);

    public SyncController(NativeDjEngine engine, DeckState a, DeckState b) {
        this.engine = engine; this.a = a; this.b = b;
        clock.scheduleAtFixedRate(this::maintainSafe, 25, 25, TimeUnit.MILLISECONDS);
    }

    public void setMode(Mode mode) { this.mode = mode == null ? Mode.BEAT : mode; }
    public Mode mode() { return mode; }

    /** One tap tempo/beat aligns target; a UI long-press should call setLocked(). */
    public synchronized void syncNow(int targetDeck) {
        DeckState target = targetDeck == NativeDjEngine.DECK_B ? b : a;
        int masterDeck = selectMaster(targetDeck);
        DeckState master = masterDeck == NativeDjEngine.DECK_A ? a : b;
        if (!ready(master) || !ready(target) || masterDeck == targetDeck) return;

        double mPos = engine.audiblePosition(masterDeck);
        double tPos = engine.position(targetDeck);
        Family mf = familyForMaster(master);
        double masterRate = engine.playbackRate(masterDeck);
        double masterBaseLocal = BeatGridMath.bpmAt(master.analysis, mPos);
        double audibleMasterBpm = masterBaseLocal * mf.factor * masterRate;
        double targetBaseLocal = BeatGridMath.bpmAt(target.analysis, tPos);
        Family tf = closestFamily(targetBaseLocal, target.analysis.bpm, audibleMasterBpm);
        double rate = clamp(audibleMasterBpm / (targetBaseLocal * tf.factor), 0.2, 2.0);
        engine.setPlaybackRate(targetDeck, rate);
        target.playbackRate = rate;

        if (mode == Mode.TEMPO) return;
        int boundary = mode == Mode.BAR ? 4 : 1;
        double masterFamilyBeat = BeatGridMath.beatAt(master.analysis, mPos) * mf.factor;
        double nextMasterFamilyBeat = Math.ceil(masterFamilyBeat / boundary - 1e-9) * boundary;
        if (nextMasterFamilyBeat <= masterFamilyBeat + 1e-7) nextMasterFamilyBeat += boundary;
        double nextMasterTime = BeatGridMath.timeAt(master.analysis, nextMasterFamilyBeat / mf.factor);
        double delayReal = Math.max(0, nextMasterTime - mPos) / Math.max(0.2, masterRate);

        double targetFamilyBeat = BeatGridMath.beatAt(target.analysis, tPos) * tf.factor;
        double nextTargetFamilyBeat = Math.ceil(targetFamilyBeat / boundary - 1e-9) * boundary;
        double tStart = BeatGridMath.timeAt(target.analysis, nextTargetFamilyBeat / tf.factor);
        engine.seek(targetDeck, tStart);
        if (!engine.isPlaying(targetDeck)) engine.playScheduled(targetDeck, delayReal);
        else applyPhase(targetDeck, masterDeck, target, master, tf, mf);
    }

    public synchronized void setLocked(int deck, boolean locked) {
        DeckState d = deck == NativeDjEngine.DECK_B ? b : a;
        d.syncLocked = locked;
        if (!locked) engine.setPhaseCorrection(deck, 0);
        else syncNow(deck);
    }

    public synchronized void toggleLocked(int deck) {
        DeckState d = deck == NativeDjEngine.DECK_B ? b : a;
        setLocked(deck, !d.syncLocked);
    }

    /** User tempo/pitch manipulation intentionally releases that deck's sync lock. */
    public synchronized void operatorTempoChanged(int deck, double rate) {
        DeckState d = deck == NativeDjEngine.DECK_B ? b : a;
        d.syncLocked = false;
        d.playbackRate = clamp(rate, .2, 2.0);
        engine.setPhaseCorrection(deck, 0);
        engine.setPlaybackRate(deck, d.playbackRate);
    }

    public Snapshot snapshot() { return snapshot; }

    private void maintainSafe() {
        try { maintain(); } catch (Throwable ignored) { /* never kill the render-support clock */ }
    }

    private synchronized void maintain() {
        boolean pa = engine.isPlaying(NativeDjEngine.DECK_A), pb = engine.isPlaying(NativeDjEngine.DECK_B);
        if (!pa || !pb) {
            if (!pa) engine.setPhaseCorrection(NativeDjEngine.DECK_A, 0);
            if (!pb) engine.setPhaseCorrection(NativeDjEngine.DECK_B, 0);
            return;
        }
        int masterDeck = selectMaster(-1);
        int slaveDeck = masterDeck == 0 ? 1 : 0;
        DeckState master = masterDeck == 0 ? a : b;
        DeckState slave = slaveDeck == 0 ? a : b;
        // A lock follows the current audible master. If both are locked the crossfader side wins.
        if (!slave.syncLocked) { engine.setPhaseCorrection(slaveDeck, 0); return; }

        double mPos = engine.audiblePosition(masterDeck), sPos = engine.audiblePosition(slaveDeck);
        Family mf = familyForMaster(master);
        double masterRate = engine.playbackRate(masterDeck);
        double mLocal = BeatGridMath.bpmAt(master.analysis, mPos);
        double audibleBpm = mLocal * mf.factor * masterRate;
        double sLocal = BeatGridMath.bpmAt(slave.analysis, sPos);
        Family sf = closestFamily(sLocal, slave.analysis.bpm, audibleBpm);
        double desired = clamp(audibleBpm / (sLocal * sf.factor), .2, 2.0);
        double current = engine.playbackRate(slaveDeck);
        // Gentle tempo convergence avoids zippering while retaining the current engine's continuous lock behavior.
        double next = current + clamp(desired - current, -0.0025, 0.0025);
        engine.setPlaybackRate(slaveDeck, next);
        slave.playbackRate = next;
        if (mode == Mode.TEMPO) { engine.setPhaseCorrection(slaveDeck, 0); return; }
        applyPhase(slaveDeck, masterDeck, slave, master, sf, mf);
    }

    private void applyPhase(int slaveDeck, int masterDeck, DeckState slave, DeckState master, Family sf, Family mf) {
        double masterRate = engine.playbackRate(masterDeck);
        double slaveRate = engine.playbackRate(slaveDeck);
        double mPos = engine.audiblePosition(masterDeck), sPos = engine.audiblePosition(slaveDeck);
        int boundary = mode == Mode.BAR ? 4 : 1;
        double mp = mod(BeatGridMath.beatAt(master.analysis, mPos) * mf.factor, boundary) / boundary;
        double sp = mod(BeatGridMath.beatAt(slave.analysis, sPos) * sf.factor, boundary) / boundary;
        double phaseBeats = wrap(sp - mp, 1.0);
        double mLocal = BeatGridMath.bpmAt(master.analysis, mPos) * mf.factor;
        double sLocal = BeatGridMath.bpmAt(slave.analysis, sPos) * sf.factor;
        double audiblePeriod = 60.0 / Math.max(1e-6, mLocal * masterRate) * boundary;
        double errorSec = phaseBeats * audiblePeriod;
        double correction = Math.abs(errorSec) * 1000.0 < LOCKED_MS ? 0 : clamp(-errorSec * 0.60, -MAX_PHASE_CORRECTION, MAX_PHASE_CORRECTION);
        engine.setPhaseCorrection(slaveDeck, correction);
        double tempoDiff = Math.abs((sLocal * slaveRate) - (mLocal * masterRate));
        snapshot = new Snapshot(masterDeck == 0 ? "A" : "B", errorSec * 1000.0, tempoDiff,
                Math.abs(errorSec) * 1000.0 < LOCKED_MS && tempoDiff < 0.05);
    }

    private int selectMaster(int targetDeck) {
        boolean pa = engine.isPlaying(0), pb = engine.isPlaying(1);
        if (pa && !pb) return 0;
        if (pb && !pa) return 1;
        if (pa && pb) {
            float x = engine.crossfader();
            if (x < .5f) return 0;
            if (x > .5f) return 1;
        }
        return targetDeck == 0 ? 1 : 0;
    }

    private static boolean ready(DeckState d) { return d != null && d.analysis != null && d.analysis.bpm > 1; }

    private static Family familyForMaster(DeckState d) {
        double bpm = d.analysis.bpm;
        while (bpm < 72) bpm *= 2;
        while (bpm > 175) bpm /= 2;
        return new Family(bpm, bpm / d.analysis.bpm);
    }

    private static Family closestFamily(double localBaseBpm, double globalBaseBpm, double targetAudibleBpm) {
        double bestBpm = globalBaseBpm, bestFactor = 1, best = Double.POSITIVE_INFINITY;
        for (double f : FAMILY) {
            double audibleCandidate = localBaseBpm * f;
            double score = Math.abs(Math.log(Math.max(1e-6, audibleCandidate) / Math.max(1e-6, targetAudibleBpm)));
            if (score < best) { best = score; bestBpm = globalBaseBpm * f; bestFactor = f; }
        }
        return new Family(bestBpm, bestFactor);
    }

    private static double phase(double position, double offset, double period, int beats) {
        double p = period * beats;
        return mod(position - offset, p) / p;
    }
    private static double nextBoundaryDelta(double position, double offset, double period, int beats) {
        double p = period * beats, x = mod(position - offset, p); return x < 1e-6 ? p : p - x;
    }
    private static double nextBoundaryAtOrAfter(double position, double offset, double period, int beats) {
        double p = period * beats; if (position <= offset) return Math.max(0, offset); return offset + Math.ceil((position - offset) / p) * p;
    }
    private static double mod(double x, double n) { return (x % n + n) % n; }
    private static double wrap(double x, double n) { return mod(x + n / 2.0, n) - n / 2.0; }
    private static double clamp(double x, double lo, double hi) { return Math.max(lo, Math.min(hi, x)); }

    @Override public void close() { clock.shutdownNow(); engine.setPhaseCorrection(0,0);engine.setPhaseCorrection(1,0); }

    private record Family(double familyBpm, double factor) {}
    public record Snapshot(String masterDeck, double phaseErrorMs, double bpmDifference, boolean locked) {}
}
