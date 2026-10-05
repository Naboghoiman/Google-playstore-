package com.djiman.bugobi.advance34.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AnalysisResult {
    public double bpm = 120.0;
    public double beatgridOffset = 0.0;
    public double bpmConfidence = 0.0;
    public double phaseConfidence = 0.0;
    public boolean gridVariable = false;
    public String gridReason = "insufficient-rhythm";
    public double gridFitRmsMs = 0.0;
    public List<Double> beatTimes = Collections.emptyList();
    public float[] overviewLow = new float[800];
    public float[] overviewMid = new float[800];
    public float[] overviewHigh = new float[800];
    public float[] peaks = new float[800];
    public float[] detailLow = new float[0];
    public float[] detailMid = new float[0];
    public float[] detailHigh = new float[0];
    public String musicalKey = "—";

    public AnalysisResult copy() {
        AnalysisResult x = new AnalysisResult();
        x.bpm = bpm;
        x.beatgridOffset = beatgridOffset;
        x.bpmConfidence = bpmConfidence;
        x.phaseConfidence = phaseConfidence;
        x.gridVariable = gridVariable;
        x.gridReason = gridReason;
        x.gridFitRmsMs = gridFitRmsMs;
        x.beatTimes = new ArrayList<>(beatTimes);
        x.overviewLow = overviewLow.clone();
        x.overviewMid = overviewMid.clone();
        x.overviewHigh = overviewHigh.clone();
        x.peaks = peaks.clone();
        x.detailLow = detailLow.clone();
        x.detailMid = detailMid.clone();
        x.detailHigh = detailHigh.clone();
        x.musicalKey = musicalKey;
        return x;
    }
}
