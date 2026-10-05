package com.djiman.bugobi.advance34.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.djiman.bugobi.advance34.DjController;
import com.djiman.bugobi.advance34.R;
import com.djiman.bugobi.advance34.automix.AutomixController;
import com.djiman.bugobi.advance34.model.AnalysisResult;
import com.djiman.bugobi.advance34.model.DeckState;
import com.djiman.bugobi.advance34.rhythm.RhythmKit;

import java.util.Locale;

/**
 * Pure Android custom View for the supplied 3.4 artwork. No WebView, HTML, CSS or
 * JavaScript is used. Reference artwork is preserved while native touch zones
 * directly manipulate the native AAudio engine.
 */
public final class DjMainView extends View implements DjController.Listener {
    public interface Host {
        void openLibraryForDeck(int deck);
        void openLibraryGeneral();
        void openLooperLoader(int slot);
        void openSampleLoader(int slot);
        void openAutomix();
        void openSettings();
    }

    private enum Screen { MAIN, DECK, MASTER, MASTER_FX, MASTER_DYNAMICS, MASTER_EQ, LOOPER, GROOVE, RHYTHM }
    private enum PadMode { SAMPLER, HOT_LOOP, SLICER, HOT_CUE, LOOP_ROLL }

    private final DjController dj;
    @SuppressWarnings("unused") private final AutomixController automix;
    private final Host host;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Bitmap mixer, deckPopup, masterOut, masterFx, masterDynamics, masterEq, looper, groove;
    private final Bitmap crossBg, crossCap, faderABg, faderACap, faderBBg, faderBCap;

    private Screen screen = Screen.MAIN;
    private PadMode padMode = PadMode.SAMPLER;
    private int activeDeck = 0;
    private int samplerBank = 0;
    private int selectedSamplePad = 0;
    private int selectedGroovePad = 0;
    private String gesture = "";
    private float downX, downY, lastAngle, gestureStart;
    private double scratchAnchor, scratchOffset;
    private long downAt, scratchLastEventTime;
    private int rhythmRowOffset = 0;
    private int rhythmPickerFamily = -1; // -1 none, -2 preset, otherwise visible family index

    public DjMainView(Context context, DjController dj, AutomixController automix, Host host) {
        super(context);
        this.dj = dj; this.automix = automix; this.host = host;
        setBackgroundColor(Color.BLACK); setKeepScreenOn(true); setFocusable(true);
        mixer = load(R.drawable.mixer_main);
        deckPopup = load(R.drawable.deck_popup);
        masterOut = load(R.drawable.master_output);
        masterFx = load(R.drawable.master_fx);
        masterDynamics = load(R.drawable.dynamics_board);
        masterEq = load(R.drawable.master_eq);
        looper = load(R.drawable.looper_board);
        groove = load(R.drawable.groove_board);
        crossBg = load(R.drawable.mixer_cross_bg); crossCap = load(R.drawable.mixer_cross_cap);
        faderABg = load(R.drawable.mixer_fader_a_bg); faderACap = load(R.drawable.mixer_fader_a_cap);
        faderBBg = load(R.drawable.mixer_fader_b_bg); faderBCap = load(R.drawable.mixer_fader_b_cap);
        dj.setListener(this);
    }

    private Bitmap load(int id) { return BitmapFactory.decodeResource(getResources(), id); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (screen == Screen.RHYTHM) {
            drawRhythmSocket(c);
        } else {
            Bitmap b = switch (screen) {
                case DECK -> deckPopup;
                case MASTER -> masterOut;
                case MASTER_FX -> masterFx;
                case MASTER_DYNAMICS -> masterDynamics;
                case MASTER_EQ -> masterEq;
                case LOOPER -> looper;
                case GROOVE -> groove;
                default -> mixer;
            };
            c.drawBitmap(b, null, new RectF(0, 0, getWidth(), getHeight()), p);
            if (screen == Screen.MAIN) drawMainLive(c);
            else if (screen == Screen.DECK) drawDeckLive(c);
            else if (screen == Screen.GROOVE) drawGrooveLive(c);
            else if (screen == Screen.MASTER || screen == Screen.MASTER_FX || screen == Screen.MASTER_DYNAMICS || screen == Screen.MASTER_EQ) drawMasterLive(c);
        }
        postInvalidateDelayed(33);
    }

    private float sx(float x) { return x / 864f * getWidth(); }
    private float sy(float y) { return y / 1536f * getHeight(); }

    private void drawMainLive(Canvas c) {
        // Live waveform replaces only the demo waveform area in the supplied artwork.
        p.setColor(Color.rgb(4, 11, 15)); c.drawRect(sx(12), sy(61), sx(852), sy(207), p);
        drawWave(c, dj.deckA.analysis, 72, 132, Color.CYAN);
        drawWave(c, dj.deckB.analysis, 139, 199, Color.rgb(255, 174, 0));
        drawTrackLabel(c, 0, 22, 80, Color.CYAN); drawTrackLabel(c, 1, 22, 151, Color.rgb(255, 174, 0));
        drawVu(c, dj.engine.vuDeck(0), 337, 333, 361, 660); drawVu(c, dj.engine.vuDeck(1), 552, 333, 576, 660);

        // Native channel fader positions.
        RectF aBg = new RectF(sx(168), sy(804), sx(262), sy(964)); c.drawBitmap(faderABg, null, aBg, p);
        RectF bBg = new RectF(sx(602), sy(804), sx(696), sy(964)); c.drawBitmap(faderBBg, null, bBg, p);
        drawChannelFader(c, 0, 168, 804, faderACap); drawChannelFader(c, 1, 602, 804, faderBCap);

        // Native crossfader position.
        RectF bg = new RectF(sx(350), sy(914), sx(515), sy(998)); c.drawBitmap(crossBg, null, bg, p);
        float x = 350 + (165 - 37) * dj.engine.crossfader();
        RectF cap = new RectF(sx(x), sy(938), sx(x + 37), sy(992)); c.drawBitmap(crossCap, null, cap, p);

        drawSamplerNames(c);
        drawMainIndicators(c);

        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2, getWidth() / 360f));
        p.setColor(Color.argb(210, 0, 210, 255)); if (dj.deckA.syncLocked) c.drawRoundRect(new RectF(sx(14), sy(622), sx(121), sy(695)), 8, 8, p);
        p.setColor(Color.argb(220, 255, 170, 0)); if (dj.deckB.syncLocked) c.drawRoundRect(new RectF(sx(742), sy(622), sx(850), sy(695)), 8, 8, p);
        if (dj.engine.isRecording()) { p.setColor(Color.RED); c.drawRoundRect(new RectF(sx(61), sy(1304), sx(194), sy(1368)), 8, 8, p); }
        p.setStyle(Paint.Style.FILL);
    }

    private void drawChannelFader(Canvas c, int deck, float left, float top, Bitmap capImage) {
        float vol = clamp(dj.state(deck).volume, 0, 1.5f);
        float n = 1f - vol / 1.5f;
        float y = top + 8 + n * 106;
        c.drawBitmap(capImage, null, new RectF(sx(left + 9), sy(y), sx(left + 85), sy(y + 46)), p);
    }

    private void drawMainIndicators(Canvas c) {
        DeckState a = dj.deckA, b = dj.deckB;
        drawKnobIndicator(c, 214, 291, 31, norm(a.trim, 0, 3.2f));
        drawKnobIndicator(c, 214, 401, 31, norm(a.eqHighDb, -26, 6));
        drawKnobIndicator(c, 214, 502, 31, norm(a.eqMidDb, -26, 6));
        drawKnobIndicator(c, 214, 599, 31, norm(a.eqLowDb, -26, 6));
        drawKnobIndicator(c, 214, 713, 31, norm(a.filter, -1, 1));
        drawKnobIndicator(c, 650, 291, 31, norm(b.trim, 0, 3.2f));
        drawKnobIndicator(c, 650, 401, 31, norm(b.eqHighDb, -26, 6));
        drawKnobIndicator(c, 650, 502, 31, norm(b.eqMidDb, -26, 6));
        drawKnobIndicator(c, 650, 599, 31, norm(b.eqLowDb, -26, 6));
        drawKnobIndicator(c, 650, 713, 31, norm(b.filter, -1, 1));
        drawKnobIndicator(c, 432, 292, 35, norm(dj.masterLevel, 0, 2));
        drawKnobIndicator(c, 432, 468, 31, norm(dj.masterEqHighDb, -26, 6));
        drawKnobIndicator(c, 432, 553, 31, norm(dj.masterEqMidDb, -26, 6));
        drawKnobIndicator(c, 432, 644, 31, norm(dj.masterEqLowDb, -26, 6));
        drawKnobIndicator(c, 341, 801, 25, dj.masterFxTime);
        drawKnobIndicator(c, 432, 801, 25, dj.masterFxDepth);
        drawKnobIndicator(c, 520, 801, 25, dj.masterFxLevel);
        drawKnobIndicator(c, 788, 1057, 27, norm(dj.padVolume(), 0, 2));
    }

    private void drawKnobIndicator(Canvas c, float cx, float cy, float radius, float normalized) {
        double a = Math.toRadians(135 + clamp(normalized, 0, 1) * 270);
        float ex = cx + (float) Math.cos(a) * radius, ey = cy + (float) Math.sin(a) * radius;
        p.setColor(Color.WHITE); p.setStrokeWidth(Math.max(2, getWidth() / 430f));
        c.drawLine(sx(cx), sy(cy), sx(ex), sy(ey), p);
    }

    private void drawSamplerNames(Canvas c) {
        if (padMode != PadMode.SAMPLER) return;
        p.setTextSize(sy(10)); p.setTextAlign(Paint.Align.CENTER);
        for (int pad = 0; pad < 8; pad++) {
            int row = pad / 4, col = pad % 4; float cx = 218 + 150 * col, cy = row == 0 ? 1168 : 1266;
            String name = dj.sampleName(samplerBank * 8 + pad); if (name.length() > 18) name = name.substring(0, 17) + "…";
            p.setColor(Color.WHITE); c.drawText(name, sx(cx), sy(cy), p);
        }
        int row = selectedSamplePad / 4, col = selectedSamplePad % 4; float l = 145 + 150 * col, t = row == 0 ? 1100 : 1196;
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2, getWidth() / 430f)); p.setColor(Color.WHITE);
        c.drawRoundRect(new RectF(sx(l), sy(t), sx(l + 132), sy(t + 83)), 7, 7, p);
        p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(sy(10)); p.setColor(dj.isSamplerSync() ? Color.GREEN : Color.LTGRAY);
        c.drawText(dj.isSamplerSync() ? "SYNC ON" : "SYNC OFF", sx(223), sy(1348), p);
        if (dj.isSamplerMuted()) { p.setColor(Color.RED); c.drawText("MUTED", sx(390), sy(1348), p); }
    }

    private void drawWave(Canvas c, AnalysisResult a, float top, float bottom, int color) {
        if (a == null || a.peaks == null || a.peaks.length < 2) return;
        float[] v = a.peaks; p.setColor(color); p.setStrokeWidth(Math.max(1, getWidth() / 900f));
        float mid = sy((top + bottom) / 2), amp = sy((bottom - top) / 2 - 2);
        for (int i = 0; i < v.length; i++) { float x = sx(15 + (835f * i / (v.length - 1))); float h = amp * Math.min(1, v[i]); c.drawLine(x, mid - h, x, mid + h, p); }
    }

    private void drawTrackLabel(Canvas c, int deck, float x, float y, int color) {
        DeckState s = dj.state(deck); p.setColor(color); p.setTextSize(sy(12));
        String title = s.info == null ? (deck == 0 ? "A — NO TRACK" : "B — NO TRACK") : String.format(Locale.US, "%s %.1f BPM · %s", deck == 0 ? "A" : "B", s.analysis == null ? 0 : s.analysis.bpm * dj.engine.playbackRate(deck), s.info.title);
        c.drawText(title, sx(x), sy(y), p);
    }

    private void drawVu(Canvas c, float level, float x1, float y1, float x2, float y2) {
        float cl = Math.min(1, Math.max(0, level)); p.setColor(Color.rgb(5, 18, 9)); c.drawRect(sx(x1), sy(y1), sx(x2), sy(y2), p);
        int bars = 28, on = Math.round(cl * bars);
        for (int i = 0; i < bars; i++) { float yy = y2 - (i + 1) * (y2 - y1) / bars + 1; p.setColor(i < on ? (i > 24 ? Color.RED : i > 19 ? Color.YELLOW : Color.GREEN) : Color.rgb(20, 38, 22)); c.drawRect(sx(x1 + 4), sy(yy), sx(x2 - 4), sy(yy + 4), p); }
    }

    private void drawDeckLive(Canvas c) {
        DeckState s = dj.state(activeDeck); float dw = 941, dh = 1672, xs = getWidth() / dw, ys = getHeight() / dh;
        p.setColor(Color.rgb(8, 13, 17)); c.drawRect(190 * xs, 480 * ys, 785 * xs, 590 * ys, p);
        p.setColor(Color.WHITE); p.setTextSize(31 * ys); p.setFakeBoldText(true); c.drawText(s.info == null ? "NO TRACK" : s.info.title, 212 * xs, 520 * ys, p); p.setFakeBoldText(false);
        p.setColor(Color.LTGRAY); p.setTextSize(21 * ys); c.drawText(s.info == null ? "Load a track" : s.info.artist, 212 * xs, 551 * ys, p);
        String meta = String.format(Locale.US, "%s / %s   ·   %s   ·   %.1f BPM", time(dj.engine.position(activeDeck)), time(dj.engine.duration(activeDeck)), s.analysis == null ? "—" : s.analysis.musicalKey, s.analysis == null ? 0 : s.analysis.bpm * dj.engine.playbackRate(activeDeck));
        c.drawText(meta, 212 * xs, 582 * ys, p);
        p.setColor(Color.CYAN); p.setTextSize(29 * ys); c.drawText(String.format(Locale.US, "%.1f", s.analysis == null ? 0 : s.analysis.bpm * dj.engine.playbackRate(activeDeck)), 430 * xs, 871 * ys, p);
        p.setTextSize(22 * ys); p.setColor(Color.CYAN); c.drawText(String.format(Locale.US, "%+.2f%% · %.1f BPM", (dj.engine.playbackRate(activeDeck) - 1) * 100, s.analysis == null ? 0 : s.analysis.bpm * dj.engine.playbackRate(activeDeck)), 620 * xs, 1250 * ys, p);
    }

    private void drawMasterLive(Canvas c) {
        float l = dj.engine.vuLeft(), r = dj.engine.vuRight(); p.setColor(Color.GREEN);
        float h = Math.min(1, l) * getHeight() * .12f; c.drawRect(getWidth() * .807f, getHeight() * .49f - h, getWidth() * .82f, getHeight() * .49f, p);
        h = Math.min(1, r) * getHeight() * .12f; c.drawRect(getWidth() * .89f, getHeight() * .49f - h, getWidth() * .903f, getHeight() * .49f, p);
        if(screen==Screen.MASTER_FX) drawRackFxLive(c);
    }

    private void drawRackFxLive(Canvas c){
        final float[] xs={154,393,634,874,154,393,634,874,154,393,634,874};
        final float[] ys={392,392,392,392,706,706,706,706,1018,1018,1018,1018};
        for(int i=0;i<12;i++) drawKnobIndicatorFx(c,xs[i],ys[i],64,dj.rackFxAmount[i]);
        drawKnobIndicatorFx(c,159,1295,64,dj.rackFxWet);
        drawKnobIndicatorFx(c,635,1295,64,clamp(dj.masterLevel,0,1));
        p.setTextAlign(Paint.Align.CENTER);p.setFakeBoldText(true);p.setTextSize(getHeight()/1536f*18);p.setColor(Color.WHITE);
        c.drawText(dj.rackFxPreset,838f/1024f*getWidth(),83f/1536f*getHeight(),p);
        String[][] modes={{"Hall","Room","Plate","Ambience"},{"1/8","1/4","1/2","1","2"},{"1/16","1/8","1/4","1/2","1"},{"Wide","Classic","Deep"},{"Classic","Light","Deep"},{"Deep","Classic","Light"},{"Warm","Soft","Hard"},{"Vocal","Punch","Glue"},{"Brickwall","Soft","Fast"},{"Low Pass","High Pass","Band Color"},{"Expand","Narrow"},{"Warm","Bright","Deep"}};
        float[] modeX={187,428,668,908,187,428,668,908,187,428,668,908};float[] modeY={286,286,286,286,601,601,601,601,914,914,914,914};
        p.setFakeBoldText(false);p.setTextSize(getHeight()/1536f*16);
        for(int i=0;i<12;i++){String[] opts=modes[i];String text=opts[Math.max(0,Math.min(opts.length-1,dj.rackFxMode[i]))];float cx=modeX[i]/1024f*getWidth(),cy=modeY[i]/1536f*getHeight();p.setColor(Color.rgb(5,9,13));c.drawRect(cx-getWidth()*.055f,cy-getHeight()*.012f,cx+getWidth()*.055f,cy+getHeight()*.012f,p);p.setColor(Color.WHITE);c.drawText(text,cx,cy+getHeight()*.006f,p);}
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,getWidth()/420f));
        float[] px={47,286,526,766,47,286,526,766,47,286,526,766};float[] py={229,229,229,229,543,543,543,543,856,856,856,856};
        for(int i=0;i<12;i++){p.setColor(dj.rackFxEnabled[i]?Color.GREEN:Color.DKGRAY);c.drawOval(new RectF(px[i]/1024f*getWidth(),py[i]/1536f*getHeight(),(px[i]+51)/1024f*getWidth(),(py[i]+51)/1536f*getHeight()),p);}
        p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawKnobIndicatorFx(Canvas c,float cx,float cy,float radius,float normalized){
        double a=Math.toRadians(135+clamp(normalized,0,1)*270);float x1=cx/1024f*getWidth(),y1=cy/1536f*getHeight();float ex=(cx+(float)Math.cos(a)*radius)/1024f*getWidth(),ey=(cy+(float)Math.sin(a)*radius)/1536f*getHeight();p.setColor(Color.WHITE);p.setStrokeWidth(Math.max(2,getWidth()/430f));c.drawLine(x1,y1,ex,ey,p);
    }

    private static String time(double sec) { if (!Double.isFinite(sec) || sec < 0) sec = 0; int s = (int) sec; return String.format(Locale.US, "%d:%02d", s / 60, s % 60); }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float[] d = design(e.getX(), e.getY()); float x = d[0], y = d[1];
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                downX = x; downY = y; downAt = SystemClock.uptimeMillis(); gesture = hit(x, y); beginContinuousGesture(x, y);
                if (gesture.equals("scratch")) { lastAngle = angle(e.getX(), e.getY()); scratchAnchor = dj.engine.position(activeDeck); scratchOffset = 0; scratchLastEventTime = e.getEventTime(); dj.engine.beginScratch(activeDeck); }
                else if (gesture.equals("autoScratchA")) dj.autoScratch.begin(0);
                else if (gesture.equals("autoScratchB")) dj.autoScratch.begin(1);
                else if (gesture.equals("dna")) dj.autoScratch.begin(activeDeck);
                handleDown(x, y); return true;
            }
            case MotionEvent.ACTION_MOVE -> { handleMove(e, x, y); return true; }
            case MotionEvent.ACTION_UP -> { handleUp(x, y); endGesture(); gesture = ""; return true; }
            case MotionEvent.ACTION_CANCEL -> { endGesture(); gesture = ""; return true; }
        }
        return true;
    }

    private float[] design(float px, float py) {
        if (screen == Screen.RHYTHM) {
            // Exact inverse of drawRhythmSocket's portrait->landscape transform.
            return new float[]{py / getHeight() * 1536f, (getWidth() - px) / getWidth() * 864f};
        }
        float w, h;
        switch (screen) {
            case DECK -> { w = 941; h = 1672; }
            case MASTER_EQ -> { w = 1536; h = 1024; }
            case MASTER, MASTER_FX -> { w = 1024; h = 1536; }
            case MASTER_DYNAMICS -> { w = 691; h = 1536; }
            default -> { w = 864; h = 1536; }
        }
        return new float[]{px / getWidth() * w, py / getHeight() * h};
    }

    private String hit(float x, float y) {
        if (screen == Screen.MAIN) {
            if (in(x,y,14,822,121,932)) return "autoScratchA";
            if (in(x,y,742,822,851,932)) return "autoScratchB";
            if (in(x,y,350,925,515,1005)) return "cross";
            if (in(x,y,165,790,270,975)) return "volA";
            if (in(x,y,594,790,705,975)) return "volB";
            if (near(x,y,214,291,50)) return "trimA"; if (near(x,y,650,291,50)) return "trimB";
            if (near(x,y,214,401,46)) return "hiA"; if (near(x,y,650,401,46)) return "hiB";
            if (near(x,y,214,502,46)) return "midA"; if (near(x,y,650,502,46)) return "midB";
            if (near(x,y,214,599,46)) return "lowA"; if (near(x,y,650,599,46)) return "lowB";
            if (near(x,y,214,713,48)) return "filterA"; if (near(x,y,650,713,48)) return "filterB";
            if (near(x,y,432,292,52)) return "masterLevel";
            if (near(x,y,432,468,45)) return "masterHi"; if (near(x,y,432,553,45)) return "masterMid"; if (near(x,y,432,644,45)) return "masterLow";
            if (near(x,y,341,801,38)) return "fxTime"; if (near(x,y,432,801,38)) return "fxDepth"; if (near(x,y,520,801,38)) return "fxLevel";
            if (near(x,y,788,1057,45)) return "padVolume";
            if (in(x,y,0,1094,864,1290)) return "pads";
            if (in(x,y,14,622,121,695) || in(x,y,742,622,850,695)) return "sync";
        }
        if (screen == Screen.GROOVE) {
            if (near(x,y,199,1000,58)) return "grooveFilter";
            if (near(x,y,353,1000,58)) return "grooveBass";
            if (near(x,y,508,1000,58)) return "grooveKey";
            if (near(x,y,664,1000,58)) return "grooveVolume";
        }
        if (screen == Screen.DECK) {
            if (in(x,y,345,1085,595,1152)) return "dna";
            if (in(x,y,220,600,720,1080)) return "scratch";
            if (in(x,y,98,1250,832,1355)) return "tempo";
        }
        if (screen == Screen.MASTER_FX) {
            float[] xs={154,393,634,874,154,393,634,874,154,393,634,874};float[] ys={392,392,392,392,706,706,706,706,1018,1018,1018,1018};
            for(int i=0;i<12;i++)if(near(x,y,xs[i],ys[i],78))return "rackFx"+i;
            if(near(x,y,159,1295,78))return "rackWet";
            if(near(x,y,635,1295,78))return "rackOutput";
        }
        if (screen == Screen.MASTER) {
            if (near(x,y,160,642,80)) return "masterTrimRack";
            if (near(x,y,337,642,80)) return "masterFilterRack";
            if (near(x,y,517,642,80)) return "masterLevelRack";
            if (near(x,y,702,642,80)) return "masterBalanceRack";
        }
        return "tap";
    }

    private void beginContinuousGesture(float x, float y) {
        int rack=rackGestureIndex(gesture);if(rack>=0){gestureStart=dj.rackFxAmount[rack];return;}
        switch (gesture) {
            case "trimA" -> gestureStart = dj.deckA.trim; case "trimB" -> gestureStart = dj.deckB.trim;
            case "hiA" -> gestureStart = dj.deckA.eqHighDb; case "hiB" -> gestureStart = dj.deckB.eqHighDb;
            case "midA" -> gestureStart = dj.deckA.eqMidDb; case "midB" -> gestureStart = dj.deckB.eqMidDb;
            case "lowA" -> gestureStart = dj.deckA.eqLowDb; case "lowB" -> gestureStart = dj.deckB.eqLowDb;
            case "filterA" -> gestureStart = dj.deckA.filter; case "filterB" -> gestureStart = dj.deckB.filter;
            case "masterLevel", "masterLevelRack" -> gestureStart = dj.masterLevel;
            case "masterHi" -> gestureStart = dj.masterEqHighDb; case "masterMid" -> gestureStart = dj.masterEqMidDb; case "masterLow" -> gestureStart = dj.masterEqLowDb;
            case "fxTime" -> gestureStart = dj.masterFxTime; case "fxDepth" -> gestureStart = dj.masterFxDepth; case "fxLevel" -> gestureStart = dj.masterFxLevel;
            case "padVolume" -> gestureStart = dj.padVolume();
            case "masterTrimRack" -> gestureStart = dj.masterTrim; case "masterFilterRack" -> gestureStart = dj.masterFilter; case "masterBalanceRack" -> gestureStart = dj.masterBalance;
            case "rackWet" -> gestureStart = dj.rackFxWet; case "rackOutput" -> gestureStart = dj.masterLevel;
            case "grooveFilter" -> gestureStart = dj.grooveFilter; case "grooveBass" -> gestureStart = dj.grooveBassDb; case "grooveKey" -> gestureStart = dj.rhythm.keySemitones(); case "grooveVolume" -> gestureStart = dj.grooveVolume;
            case "volA", "volB", "tempo" -> updateContinuous(x, y);
        }
    }

    private void handleDown(float x, float y) {
        if (screen == Screen.MAIN) {
            if (gesture.equals("cross")) { setCrossFromX(x); return; }
            if (in(x,y,0,1094,864,1290) && padMode == PadMode.LOOP_ROLL) { dj.loopRoll(dj.masterDeck(), padAt(x,y), true); gesture = "roll"; return; }
            if (in(x,y,17,535,120,610)) { dj.cueHeld(0,true); gesture = "cueA"; return; }
            if (in(x,y,744,535,847,610)) { dj.cueHeld(1,true); gesture = "cueB"; return; }
        }
    }

    private void handleMove(MotionEvent e, float x, float y) {
        if (gesture.equals("cross")) { setCrossFromX(x); return; }
        if (gesture.equals("scratch")) {
            float a = angle(e.getX(), e.getY()), delta = wrapAngle(a - lastAngle); long now = e.getEventTime(); double dt = Math.max(.001, (now - scratchLastEventTime) / 1000.0);
            lastAngle = a; scratchLastEventTime = now; scratchOffset += delta / (Math.PI * 2) * 1.8;
            double rate = Math.max(-4, Math.min(4, (delta / dt) / 3.4906585)); dj.engine.moveScratch(activeDeck, Math.max(0, scratchAnchor + scratchOffset), rate); return;
        }
        updateContinuous(x, y);
    }

    private void updateContinuous(float x, float y) {
        float dy = downY - y;
        int rack=rackGestureIndex(gesture);if(rack>=0){dj.setRackFxAmount(rack,clamp(gestureStart+dy/300f,0,1));return;}
        switch (gesture) {
            case "volA" -> dj.setVolume(0, 1.5f * (1 - clamp((y - 810) / 150f, 0, 1)));
            case "volB" -> dj.setVolume(1, 1.5f * (1 - clamp((y - 810) / 150f, 0, 1)));
            case "trimA" -> dj.setTrim(0, clamp(gestureStart + dy / 110f, 0, 3.2f), true);
            case "trimB" -> dj.setTrim(1, clamp(gestureStart + dy / 110f, 0, 3.2f), true);
            case "hiA" -> dj.setEq(0, dj.deckA.eqLowDb, dj.deckA.eqMidDb, clamp(gestureStart + dy / 5.5f, -26, 6));
            case "hiB" -> dj.setEq(1, dj.deckB.eqLowDb, dj.deckB.eqMidDb, clamp(gestureStart + dy / 5.5f, -26, 6));
            case "midA" -> dj.setEq(0, dj.deckA.eqLowDb, clamp(gestureStart + dy / 5.5f, -26, 6), dj.deckA.eqHighDb);
            case "midB" -> dj.setEq(1, dj.deckB.eqLowDb, clamp(gestureStart + dy / 5.5f, -26, 6), dj.deckB.eqHighDb);
            case "lowA" -> dj.setEq(0, clamp(gestureStart + dy / 5.5f, -26, 6), dj.deckA.eqMidDb, dj.deckA.eqHighDb);
            case "lowB" -> dj.setEq(1, clamp(gestureStart + dy / 5.5f, -26, 6), dj.deckB.eqMidDb, dj.deckB.eqHighDb);
            case "filterA" -> dj.setFilter(0, clamp(gestureStart + dy / 90f, -1, 1));
            case "filterB" -> dj.setFilter(1, clamp(gestureStart + dy / 90f, -1, 1));
            case "masterLevel", "masterLevelRack" -> dj.setMasterLevel(clamp(gestureStart + dy / 100f, 0, 2));
            case "masterHi" -> dj.setMasterEq(dj.masterEqLowDb, dj.masterEqMidDb, clamp(gestureStart + dy / 5.5f, -26, 6));
            case "masterMid" -> dj.setMasterEq(dj.masterEqLowDb, clamp(gestureStart + dy / 5.5f, -26, 6), dj.masterEqHighDb);
            case "masterLow" -> dj.setMasterEq(clamp(gestureStart + dy / 5.5f, -26, 6), dj.masterEqMidDb, dj.masterEqHighDb);
            case "fxTime" -> dj.setMasterFx(dj.masterFxEnabled,dj.masterFxType,clamp(gestureStart+dy/160f,0,1),dj.masterFxDepth,dj.masterFxLevel);
            case "fxDepth" -> dj.setMasterFx(dj.masterFxEnabled,dj.masterFxType,dj.masterFxTime,clamp(gestureStart+dy/160f,0,1),dj.masterFxLevel);
            case "fxLevel" -> dj.setMasterFx(dj.masterFxEnabled,dj.masterFxType,dj.masterFxTime,dj.masterFxDepth,clamp(gestureStart+dy/160f,0,1));
            case "padVolume" -> dj.setPadVolume(clamp(gestureStart + dy / 90f, 0, 2));
            case "masterTrimRack" -> dj.setMasterTrim(clamp(gestureStart + dy / 80f, 0, 4));
            case "masterFilterRack" -> dj.setMasterFilter(clamp(gestureStart + dy / 100f, -1, 1));
            case "masterBalanceRack" -> dj.setMasterBalance(clamp(gestureStart + dy / 100f, -1, 1));
            case "rackWet" -> dj.setRackFxWet(clamp(gestureStart + dy / 300f, 0, 1));
            case "rackOutput" -> dj.setMasterLevel(clamp(gestureStart + dy / 300f, 0, 1));
            case "grooveFilter" -> dj.setGrooveFilter(clamp(gestureStart + dy / 120f, -1, 1));
            case "grooveBass" -> dj.setGrooveBass(clamp(gestureStart + dy / 8f, -12, 6));
            case "grooveKey" -> dj.rhythm.setKeySemitones(Math.round(clamp(gestureStart + dy / 8f, -12, 12)));
            case "grooveVolume" -> dj.setGrooveVolume(clamp(gestureStart + dy / 140f, 0, 1));
            case "tempo" -> { DeckState s=dj.state(activeDeck); double range=s.tempoRangePercent; double pct=clamp((x-98)/(832-98)*2-1,-1,1)*range; dj.setTempo(activeDeck,1+pct/100.0); }
        }
    }

    private void handleUp(float x, float y) {
        if (gesture.equals("scratch") || gesture.equals("autoScratchA") || gesture.equals("autoScratchB") || gesture.equals("dna")) return;
        if (isContinuous(gesture)) return;

        if (screen == Screen.MAIN) {
            if (gesture.equals("cueA")) { dj.cueHeld(0,false); gesture="released"; return; }
            if (gesture.equals("cueB")) { dj.cueHeld(1,false); gesture="released"; return; }
            if (gesture.equals("roll")) { dj.loopRoll(dj.masterDeck(), padAt(x,y), false); gesture="released"; return; }
            if (in(x,y,80,0,238,58) || in(x,y,5,1378,165,1483) || in(x,y,700,1375,860,1486)) { host.openLibraryGeneral(); return; }
            if (in(x,y,545,0,720,58)) { screen=Screen.MASTER; invalidate(); return; }
            if (in(x,y,726,0,861,58)) { host.openSettings(); return; }
            if (in(x,y,10,270,130,420)) { activeDeck=0; screen=Screen.DECK; invalidate(); return; }
            if (in(x,y,735,270,858,420)) { activeDeck=1; screen=Screen.DECK; invalidate(); return; }
            if (in(x,y,15,430,123,510)) { dj.playPause(0); return; }
            if (in(x,y,741,430,852,510)) { dj.playPause(1); return; }
            if (in(x,y,13,620,125,700)) { if(SystemClock.uptimeMillis()-downAt>500)dj.syncLock(0);else dj.syncTap(0); return; }
            if (in(x,y,739,620,854,700)) { if(SystemClock.uptimeMillis()-downAt>500)dj.syncLock(1);else dj.syncTap(1); return; }
            if (in(x,y,315,710,365,765)) { dj.cycleMasterFx(-1); return; }
            if (in(x,y,500,710,555,765)) { dj.cycleMasterFx(1); return; }
            if (in(x,y,315,850,430,915)) { dj.setMasterFx(!dj.masterFxEnabled,dj.masterFxType,dj.masterFxTime,dj.masterFxDepth,dj.masterFxLevel); return; }
            if (y>1038&&y<1100) { if(x<190)padMode=PadMode.SAMPLER;else if(x<333)padMode=PadMode.HOT_LOOP;else if(x<470)padMode=PadMode.SLICER;else if(x<609)padMode=PadMode.HOT_CUE;else padMode=PadMode.LOOP_ROLL; return; }
            if (in(x,y,55,1095,132,1292) && padMode==PadMode.SAMPLER) { samplerBank=Math.max(0,Math.min(3,(int)((y-1095)/49))); return; }
            if (in(x,y,130,1095,750,1292)) {
                int pad=padAt(x,y); selectedSamplePad=pad;
                if(padMode==PadMode.HOT_CUE && SystemClock.uptimeMillis()-downAt>500) dj.clearHotCue(dj.masterDeck(),pad); else performPad(pad);
                return;
            }
            if (in(x,y,758,1095,842,1192) && padMode==PadMode.SAMPLER) { host.openSampleLoader(samplerBank*8+selectedSamplePad); return; }
            if (in(x,y,758,1192,842,1295) && padMode==PadMode.SAMPLER) { Toast.makeText(getContext(),"Sampler banks are saved automatically on this device.",Toast.LENGTH_SHORT).show(); return; }
            if (in(x,y,60,1302,194,1368)) { dj.toggleRecording(); return; }
            if (in(x,y,205,1302,344,1368)) { dj.setSamplerSync(!dj.isSamplerSync()); return; }
            if (in(x,y,356,1302,498,1368)) { dj.setSamplerMuted(!dj.isSamplerMuted()); return; }
            if (in(x,y,508,1302,650,1368)) { dj.clearSample(samplerBank*8+selectedSamplePad); return; }
            if (in(x,y,658,1302,805,1368)) { dj.clearSampleBank(samplerBank); return; }
            if (in(x,y,170,1375,344,1486)) { host.openAutomix(); return; }
            if (in(x,y,345,1375,523,1486)) { screen=Screen.GROOVE; invalidate(); return; }
            if (in(x,y,522,1375,698,1486)) { screen=Screen.MASTER_FX; invalidate(); return; }
            return;
        }

        if (screen == Screen.DECK) {
            if (in(x,y,785,414,884,502)) { screen=Screen.MAIN; invalidate(); return; }
            if (in(x,y,72,592,219,668)) { host.openLibraryForDeck(activeDeck); return; }
            if (in(x,y,72,735,185,802)) { DeckState s=dj.state(activeDeck); dj.setKeyLock(activeDeck,!s.keyLock); return; }
            if (in(x,y,83,843,171,902)) { dj.changeKey(activeDeck,dj.state(activeDeck).keySemitones-1); return; }
            if (in(x,y,82,957,171,1018)) { dj.changeKey(activeDeck,dj.state(activeDeck).keySemitones+1); return; }
            if (in(x,y,247,1156,347,1224)) { dj.cue(activeDeck); return; }
            if (in(x,y,357,1156,459,1224)) { dj.playPause(activeDeck); return; }
            if (in(x,y,468,1156,573,1224)) { dj.syncTap(activeDeck); return; }
            if (in(x,y,583,1156,687,1224)) { dj.syncLock(activeDeck); return; }
            if (in(x,y,210,1363,329,1435)) { dj.pitchBend(activeDeck,-.04); postDelayed(()->dj.pitchBend(activeDeck,0),180); return; }
            if (in(x,y,592,1363,720,1435)) { dj.pitchBend(activeDeck,.04); postDelayed(()->dj.pitchBend(activeDeck,0),180); return; }
            if (in(x,y,337,1363,470,1435)) { dj.setTempo(activeDeck,1); return; }
            if (in(x,y,472,1363,590,1435)) { DeckState s=dj.state(activeDeck); s.tempoRangePercent=s.tempoRangePercent<10?16:s.tempoRangePercent<20?50:8; Toast.makeText(getContext(),"Tempo range ±"+(int)s.tempoRangePercent+"%",Toast.LENGTH_SHORT).show(); return; }
            return;
        }

        if (screen == Screen.GROOVE) {
            // Exact 680x992 packaged dialog mapped into the 864-wide mixer reference (x=92,y=296).
            if (in(x,y,667,313,733,374)) { screen=Screen.MAIN; invalidate(); return; }
            if (in(x,y,129,384,383,421)) { screen=Screen.RHYTHM; rhythmPickerFamily=-1; invalidate(); return; }
            for(int pad=0;pad<12;pad++){int col=pad%4,row=pad/4;float l=129+col*155,t=433+row*151;if(in(x,y,l,t,l+143,t+138)){selectedGroovePad=pad;if(SystemClock.uptimeMillis()-downAt<450)dj.toggleLooperSlot(pad);invalidate();return;}}
            if(in(x,y,141,1137,324,1242)){host.openLooperLoader(selectedGroovePad);return;}
            if(in(x,y,340,1137,526,1242)){Toast.makeText(getContext(),"Groove pads and knob settings are saved on this device.",Toast.LENGTH_SHORT).show();return;}
            if(in(x,y,541,1137,727,1242)){dj.clearGrooveSlot(selectedGroovePad);return;}
            return;
        }

        if (screen == Screen.RHYTHM) {
            handleRhythmTap(x,y);
            return;
        }

        if (screen == Screen.LOOPER) {
            if (in(x,y,655,225,746,304) || in(x,y,617,1284,744,1370)) { screen=Screen.MAIN; invalidate(); return; }
            if (in(x,y,304,844,562,914)) { dj.syncPlayingLoopers(); return; }
            int slot=looperSlotAt(x);
            if (slot>=0 && y>510&&y<575) { host.openLooperLoader(slot); return; }
            if (slot>=0 && y>575&&y<655) { dj.toggleLooperSlot(slot); return; }
            if (slot>=0 && y>775&&y<840) { dj.engine.clearLooperSlot(slot); return; }
            return;
        }

        if (screen == Screen.MASTER_FX) {
            if(in(x,y,944,31,1012,110)){screen=Screen.MASTER;invalidate();return;}
            if(in(x,y,47,24,223,106)||in(x,y,817,1214,939,1357)){dj.setRackFxMaster(!dj.rackFxAnyEnabled());return;}
            if(in(x,y,746,45,930,103)){dj.cycleRackFxPreset(1);return;}
            int module=rackPowerAt(x,y);if(module>=0){dj.toggleRackFx(module);return;}
            module=rackModeAt(x,y);if(module>=0){dj.cycleRackFxMode(module,rackModeCount(module));return;}
            return;
        }

        if (screen == Screen.MASTER) {
            if (in(x,y,510,28,610,118)) { screen=Screen.MAIN; invalidate(); return; }
            if (in(x,y,713,28,820,118)) { screen=Screen.LOOPER; invalidate(); return; }
            if (in(x,y,825,28,930,118)) { host.openSettings(); return; }
            if (in(x,y,285,430,535,490)) { screen=Screen.MASTER_FX; invalidate(); return; }
            if (in(x,y,525,430,744,490)) { screen=Screen.MASTER_DYNAMICS; invalidate(); return; }
            if (in(x,y,747,430,967,490)) { screen=Screen.MASTER_EQ; invalidate(); return; }
            return;
        }

        if (screen == Screen.MASTER_FX || screen == Screen.MASTER_DYNAMICS || screen == Screen.MASTER_EQ) {
            // Do not steal the MASTER FX power control. Rack screens return through Android back;
            // only explicit back/menu areas on artwork are used here when one exists.
            if (screen==Screen.MASTER_EQ && in(x,y,70,335,165,410)) { screen=Screen.MASTER; invalidate(); return; }
        }
    }

    private void drawGrooveLive(Canvas c) {
        // Coordinates match the packaged 680x992 Groove dialog at x=92,y=296.
        RectF r=new RectF(sx(129),sy(384),sx(383),sy(421));
        p.setColor(Color.rgb(38,54,66));c.drawRoundRect(r,5,5,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,getWidth()/430f));p.setColor(Color.rgb(164,178,188));c.drawRoundRect(r,5,5,p);p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);p.setTextSize(sy(14));p.setFakeBoldText(true);p.setColor(Color.rgb(195,243,255));c.drawText("RHYTHM SOCKET ▼",sx(256),sy(409),p);p.setFakeBoldText(false);

        for(int pad=0;pad<12;pad++){
            int col=pad%4,row=pad/4;float l=129+col*155,t=433+row*151;
            if(pad==selectedGroovePad){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(3,getWidth()/300f));p.setColor(Color.WHITE);c.drawRoundRect(new RectF(sx(l),sy(t),sx(l+143),sy(t+138)),8,8,p);p.setStyle(Paint.Style.FILL);}
            if(dj.engine.isLooperSlotPlaying(pad)){p.setColor(Color.argb(115,89,255,177));c.drawRoundRect(new RectF(sx(l+5),sy(t+5),sx(l+138),sy(t+133)),8,8,p);}
        }
        p.setTextAlign(Paint.Align.LEFT);p.setTextSize(sy(12));p.setColor(Color.rgb(214,245,255));String name=dj.grooveName(selectedGroovePad);if(name.length()>34)name=name.substring(0,33)+"…";c.drawText("PAD "+(selectedGroovePad+1)+" · "+(name.equals("EMPTY")?"SELECT PAD → UPLOAD":name),sx(135),sy(914),p);
        drawKnobIndicator(c,199,1000,34,norm(dj.grooveFilter,-1,1));
        drawKnobIndicator(c,353,1000,34,norm(dj.grooveBassDb,-12,6));
        drawKnobIndicator(c,508,1000,34,norm(dj.rhythm.keySemitones(),-12,12));
        drawKnobIndicator(c,664,1000,34,norm(dj.grooveVolume,0,1));
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawRhythmSocket(Canvas c) {
        c.drawColor(Color.rgb(2,8,13));
        c.save();
        c.translate(getWidth(),0);
        c.rotate(90);
        c.scale(getHeight()/1536f,getWidth()/864f);

        // Metal panel matching the packaged Rhythm Socket styling.
        p.setColor(Color.rgb(65,82,95));c.drawRoundRect(new RectF(8,8,1528,856),18,18,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(Color.rgb(155,174,189));c.drawRoundRect(new RectF(8,8,1528,856),18,18,p);p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(23,35,45));c.drawRoundRect(new RectF(14,14,1522,850),14,14,p);
        p.setColor(Color.rgb(62,79,93));c.drawRoundRect(new RectF(20,20,1516,844),11,11,p);

        p.setTextAlign(Paint.Align.LEFT);p.setFakeBoldText(true);p.setTextSize(26);p.setColor(Color.WHITE);c.drawText("RHYTHM SOCKET",38,57,p);p.setFakeBoldText(false);
        drawRhythmButton(c,350,25,500,70,dj.rhythm.isActive()?"■ STOP":"▶ START",dj.rhythm.isActive());
        p.setTextSize(17);p.setColor(Color.rgb(214,245,255));c.drawText(dj.rhythm.statusText(),520,55,p);
        drawRhythmButton(c,1325,25,1495,70,"LOOPER ✕",false);

        final int visible=10;
        final float top=92f,rowH=61f,labelX=34f,labelW=196f,stepX=242f,stepW=72f,gap=4f;
        int current=dj.rhythm.currentStep();
        for(int vr=0;vr<visible;vr++){
            int fi=rhythmRowOffset+vr;
            if(fi>=RhythmKit.FAMILIES.size())break;
            RhythmKit.Family f=RhythmKit.FAMILIES.get(fi);
            float y=top+vr*rowH;
            p.setColor(Color.argb(80,155,174,189));c.drawRect(28,y-3,1498,y-2,p);
            RectF voice=new RectF(labelX,y,labelX+labelW,y+28);
            p.setColor(dj.rhythm.rowEnabled(f.row)?Color.rgb(54,70,82):Color.rgb(36,45,54));c.drawRoundRect(voice,5,5,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);p.setColor(Color.rgb(155,171,186));c.drawRoundRect(voice,5,5,p);p.setStyle(Paint.Style.FILL);
            p.setColor(dj.rhythm.rowEnabled(f.row)?Color.rgb(166,236,139):Color.rgb(32,45,54));c.drawRect(labelX+7,y+6,labelX+13,y+22,p);
            p.setTextSize(13);p.setFakeBoldText(true);p.setColor(Color.WHITE);c.drawText(f.name,labelX+20,y+19,p);p.setFakeBoldText(false);
            RectF sound=new RectF(labelX,y+31,labelX+labelW,y+57);p.setColor(Color.rgb(38,54,66));c.drawRoundRect(sound,4,4,p);
            p.setStyle(Paint.Style.STROKE);p.setColor(Color.rgb(105,140,159));c.drawRoundRect(sound,4,4,p);p.setStyle(Paint.Style.FILL);
            p.setTextSize(11);p.setColor(Color.rgb(184,239,255));String sn=dj.rhythm.soundName(f.id);if(sn.length()>24)sn=sn.substring(0,23)+"…";c.drawText(sn+" ▼",labelX+7,y+48,p);
            for(int st=0;st<16;st++){
                float x=stepX+st*(stepW+gap);RectF cell=new RectF(x,y+4,x+stepW,y+54);
                boolean on=dj.rhythm.step(f.row,st),warm=((st/4)&1)==1;
                int fill=on?(warm?Color.rgb(213,166,178):Color.rgb(180,212,223)):(warm?Color.rgb(97,84,91):Color.rgb(67,83,93));
                if(!dj.rhythm.rowEnabled(f.row))fill=Color.rgb(48,55,60);
                p.setColor(fill);c.drawRoundRect(cell,4,4,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(st==current?3:1);p.setColor(st==current?Color.rgb(111,228,255):Color.rgb(37,50,58));c.drawRoundRect(cell,4,4,p);p.setStyle(Paint.Style.FILL);
                p.setTextAlign(Paint.Align.CENTER);p.setTextSize(10);p.setColor(on?Color.rgb(35,55,66):Color.rgb(235,241,244));c.drawText(Integer.toString(st+1),x+stepW/2,y+36,p);p.setTextAlign(Paint.Align.LEFT);
            }
        }
        drawRhythmButton(c,1498,110,1524,180,"▲",false);drawRhythmButton(c,1498,637,1524,707,"▼",false);
        p.setTextSize(10);p.setColor(Color.rgb(211,235,250));p.setTextAlign(Paint.Align.CENTER);c.drawText((rhythmRowOffset+1)+"–"+Math.min(RhythmKit.FAMILIES.size(),rhythmRowOffset+visible)+" / "+RhythmKit.FAMILIES.size(),1511,410,p);p.setTextAlign(Paint.Align.LEFT);

        p.setColor(Color.argb(110,166,186,198));c.drawRect(28,716,1498,718,p);
        p.setTextSize(14);p.setColor(Color.rgb(203,219,229));c.drawText("Main looper",38,753,p);p.setFakeBoldText(true);p.setColor(Color.rgb(192,240,255));c.drawText("BASS · KEY · FILTER · VOLUME",38,777,p);p.setFakeBoldText(false);
        drawRhythmButton(c,385,734,720,800,dj.rhythm.kitPresetName()+" ▼",false);
        drawRhythmButton(c,1000,734,1135,800,"SAVE",false);drawRhythmButton(c,1145,734,1280,800,"CLEAR",false);drawRhythmButton(c,1290,734,1425,800,"RESET",false);

        if(rhythmPickerFamily!=-1)drawRhythmPicker(c);
        c.restore();
    }

    private void drawRhythmButton(Canvas c,float l,float t,float r,float b,String text,boolean active){
        p.setColor(active?Color.rgb(54,93,78):Color.rgb(39,55,66));c.drawRoundRect(new RectF(l,t,r,b),6,6,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(active?Color.rgb(123,234,193):Color.rgb(155,171,186));c.drawRoundRect(new RectF(l,t,r,b),6,6,p);p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);p.setTextSize(14);p.setFakeBoldText(true);p.setColor(active?Color.rgb(181,255,226):Color.WHITE);c.drawText(text,(l+r)/2,(t+b)/2+5,p);p.setFakeBoldText(false);p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawRhythmPicker(Canvas c){
        p.setColor(Color.argb(235,18,33,45));c.drawRoundRect(new RectF(160,90,1376,790),12,12,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(Color.rgb(155,174,189));c.drawRoundRect(new RectF(160,90,1376,790),12,12,p);p.setStyle(Paint.Style.FILL);
        String title=rhythmPickerFamily==-2?"SOUND PRESETS":RhythmKit.FAMILIES.get(rhythmPickerFamily).name+" SOUNDS";
        p.setTextSize(24);p.setFakeBoldText(true);p.setColor(Color.WHITE);c.drawText(title,195,135,p);p.setFakeBoldText(false);
        p.setTextSize(14);p.setColor(Color.rgb(214,235,246));c.drawText(rhythmPickerFamily==-2?"Changes sounds only. Your rhythm stays the same.":"Choose a variation for the next hit.",195,163,p);
        drawRhythmButton(c,1160,108,1335,160,"BACK ✕",false);
        java.util.List<String[]> opts=new java.util.ArrayList<>();
        if(rhythmPickerFamily==-2){opts.add(new String[]{"default","DJ DEFAULTS"});for(RhythmKit.Preset pr:RhythmKit.PRESETS)opts.add(new String[]{pr.id,pr.name});}
        else {RhythmKit.Family f=RhythmKit.FAMILIES.get(rhythmPickerFamily);for(RhythmKit.Sound so:f.sounds)opts.add(new String[]{so.id,so.name});}
        float left=195,top=195,w=535,h=70,gx=25,gy=14;
        for(int i=0;i<opts.size();i++){int col=i%2,row=i/2;float l=left+col*(w+gx),t=top+row*(h+gy);boolean selected=rhythmPickerFamily==-2?opts.get(i)[0].equals(dj.rhythm.kitPreset()):opts.get(i)[0].equals(dj.rhythm.soundId(RhythmKit.FAMILIES.get(rhythmPickerFamily).id));drawRhythmButton(c,l,t,l+w,t+h,opts.get(i)[1],selected);}
    }

    private void handleRhythmTap(float x,float y){
        if(rhythmPickerFamily!=-1){
            if(in(x,y,1160,108,1335,160)){rhythmPickerFamily=-1;invalidate();return;}
            java.util.List<String[]> opts=new java.util.ArrayList<>();
            if(rhythmPickerFamily==-2){opts.add(new String[]{"default","DJ DEFAULTS"});for(RhythmKit.Preset pr:RhythmKit.PRESETS)opts.add(new String[]{pr.id,pr.name});}
            else {RhythmKit.Family f=RhythmKit.FAMILIES.get(rhythmPickerFamily);for(RhythmKit.Sound so:f.sounds)opts.add(new String[]{so.id,so.name});}
            float left=195,top=195,w=535,h=70,gx=25,gy=14;
            for(int i=0;i<opts.size();i++){int col=i%2,row=i/2;float l=left+col*(w+gx),t=top+row*(h+gy);if(in(x,y,l,t,l+w,t+h)){if(rhythmPickerFamily==-2)dj.rhythm.choosePreset(opts.get(i)[0]);else dj.rhythm.chooseSound(RhythmKit.FAMILIES.get(rhythmPickerFamily).id,opts.get(i)[0]);rhythmPickerFamily=-1;invalidate();return;}}
            return;
        }
        if(in(x,y,350,25,500,70)){if(!dj.rhythm.isLoading())dj.rhythm.toggleActive();else Toast.makeText(getContext(),"Rhythm sounds are still loading",Toast.LENGTH_SHORT).show();return;}
        if(in(x,y,1325,25,1495,70)){screen=Screen.GROOVE;invalidate();return;}
        if(in(x,y,1490,95,1535,200)){rhythmRowOffset=Math.max(0,rhythmRowOffset-1);invalidate();return;}
        if(in(x,y,1490,625,1535,720)){rhythmRowOffset=Math.min(Math.max(0,RhythmKit.FAMILIES.size()-10),rhythmRowOffset+1);invalidate();return;}
        final float top=92f,rowH=61f,labelX=34f,labelW=196f,stepX=242f,stepW=72f,gap=4f;
        for(int vr=0;vr<10;vr++){
            int fi=rhythmRowOffset+vr;if(fi>=RhythmKit.FAMILIES.size())break;RhythmKit.Family f=RhythmKit.FAMILIES.get(fi);float ry=top+vr*rowH;
            if(in(x,y,labelX,ry,labelX+labelW,ry+28)){dj.rhythm.toggleRow(f.row);return;}
            if(in(x,y,labelX,ry+31,labelX+labelW,ry+57)){rhythmPickerFamily=fi;invalidate();return;}
            if(y>=ry+2&&y<=ry+57&&x>=stepX&&x<=stepX+16*(stepW+gap)){int st=(int)((x-stepX)/(stepW+gap));if(st>=0&&st<16){dj.rhythm.toggleStep(f.row,st);return;}}
        }
        if(in(x,y,385,734,720,800)){rhythmPickerFamily=-2;invalidate();return;}
        if(in(x,y,1000,734,1135,800)){dj.rhythm.save();Toast.makeText(getContext(),"Pattern and drum sounds saved. Main looper SAVE stores the sound knobs.",Toast.LENGTH_SHORT).show();return;}
        if(in(x,y,1145,734,1280,800)){dj.rhythm.clearPattern();return;}
        if(in(x,y,1290,734,1425,800)){dj.rhythm.reset();return;}
    }

    private boolean isContinuous(String g) {
        if(rackGestureIndex(g)>=0)return true;
        return g.equals("volA")||g.equals("volB")||g.equals("trimA")||g.equals("trimB")||g.equals("hiA")||g.equals("hiB")||g.equals("midA")||g.equals("midB")||g.equals("lowA")||g.equals("lowB")||g.equals("filterA")||g.equals("filterB")||g.equals("masterLevel")||g.equals("masterHi")||g.equals("masterMid")||g.equals("masterLow")||g.equals("fxTime")||g.equals("fxDepth")||g.equals("fxLevel")||g.equals("padVolume")||g.equals("tempo")||g.equals("masterTrimRack")||g.equals("masterFilterRack")||g.equals("masterLevelRack")||g.equals("masterBalanceRack")||g.equals("rackWet")||g.equals("rackOutput")||g.equals("grooveFilter")||g.equals("grooveBass")||g.equals("grooveKey")||g.equals("grooveVolume");
    }

    private void endGesture() {
        if (gesture.equals("scratch")) dj.engine.endScratch(activeDeck);
        else if (gesture.equals("autoScratchA")) dj.autoScratch.end(0);
        else if (gesture.equals("autoScratchB")) dj.autoScratch.end(1);
        else if (gesture.equals("dna")) dj.autoScratch.end(activeDeck);
        else if (gesture.equals("roll")) dj.loopRoll(dj.masterDeck(), padAt(downX,downY), false);
        else if (gesture.equals("cueA")) dj.cueHeld(0,false);
        else if (gesture.equals("cueB")) dj.cueHeld(1,false);
    }

    private static int rackGestureIndex(String g){if(g==null||!g.startsWith("rackFx"))return -1;try{return Integer.parseInt(g.substring(6));}catch(Exception ignored){return -1;}}
    private static int rackPowerAt(float x,float y){float[] px={47,286,526,766,47,286,526,766,47,286,526,766};float[] py={229,229,229,229,543,543,543,543,856,856,856,856};for(int i=0;i<12;i++)if(in(x,y,px[i],py[i],px[i]+58,py[i]+58))return i;return -1;}
    private static int rackModeAt(float x,float y){float[] px={120,361,601,841,120,361,601,841,120,361,601,841};float[] py={263,263,263,263,578,578,578,578,891,891,891,891};for(int i=0;i<12;i++)if(in(x,y,px[i],py[i],px[i]+134,py[i]+40))return i;return -1;}
    private static int rackModeCount(int module){return switch(module){case 0->4;case 1,2->5;case 10->2;default->3;};}

    private void performPad(int pad) {
        int deck=dj.masterDeck();
        switch (padMode) {
            case SAMPLER -> dj.triggerSample(samplerBank*8+pad);
            case HOT_LOOP -> dj.hotLoop(deck,pad);
            case SLICER -> dj.slicer(deck,pad);
            case HOT_CUE -> dj.hotCue(deck,pad);
            case LOOP_ROLL -> { }
        }
    }

    private int padAt(float x,float y) { int col=Math.max(0,Math.min(3,(int)((x-145)/150))); int row=y>1190?1:0; return row*4+col; }
    private void setCrossFromX(float x) { dj.setCrossfader(clamp((x-358)/(507-358),0,1)); }
    private float angle(float x,float y) { return (float)Math.atan2(y-getHeight()*.50f,x-getWidth()*.50f); }
    private static float wrapAngle(float a) { while(a>Math.PI)a-=Math.PI*2;while(a<-Math.PI)a+=Math.PI*2;return a; }
    private int looperSlotAt(float x) { if(x>=125&&x<276)return 0;if(x>=278&&x<431)return 1;if(x>=435&&x<588)return 2;if(x>=590&&x<744)return 3;return -1; }
    private static boolean in(float x,float y,float l,float t,float r,float b) { return x>=l&&x<=r&&y>=t&&y<=b; }
    private static boolean near(float x,float y,float cx,float cy,float radius) { float dx=x-cx,dy=y-cy;return dx*dx+dy*dy<=radius*radius; }
    private static float clamp(float v,float lo,float hi) { return Math.max(lo,Math.min(hi,v)); }
    private static float norm(float v,float lo,float hi) { return clamp((v-lo)/(hi-lo),0,1); }

    public boolean goBack() {
        if(screen==Screen.MASTER_FX || screen==Screen.MASTER_DYNAMICS || screen==Screen.MASTER_EQ){screen=Screen.MASTER;invalidate();return true;}
        if(screen==Screen.RHYTHM){screen=Screen.GROOVE;rhythmPickerFamily=-1;invalidate();return true;}
        if(screen==Screen.DECK || screen==Screen.MASTER || screen==Screen.LOOPER || screen==Screen.GROOVE){screen=Screen.MAIN;invalidate();return true;}
        return false;
    }
    @Override public void onDeckLoading(int deck, com.djiman.bugobi.advance34.model.TrackInfo track) { invalidate(); }
    @Override public void onDeckLoaded(int deck, DeckState state) { invalidate(); }
    @Override public void onDeckError(int deck, com.djiman.bugobi.advance34.model.TrackInfo track, Throwable error) { Toast.makeText(getContext(),"Track load failed: "+error.getMessage(),Toast.LENGTH_LONG).show(); invalidate(); }
    @Override public void onStateChanged() { postInvalidate(); }
}
