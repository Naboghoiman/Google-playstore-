package com.djiman.bugobi.advance34.analysis;

import com.djiman.bugobi.advance34.model.PcmTrack;

import java.util.ArrayList;
import java.util.List;

/** Native port of auto-trim.js's 400 ms gated energy measurement and target calculation. */
public final class AutoTrim {
    public static final double MAXIMUM = Math.pow(10.0, 10.0 / 20.0);
    private AutoTrim() {}

    public record Level(double db, double peak, boolean valid, double activeSeconds) {}

    public static Level measure(PcmTrack track) {
        int sr = track.sampleRate, frames = (int)Math.min(Integer.MAX_VALUE, track.frames);
        int block = Math.max(1, Math.round(sr * .4f));
        List<Double> energy = new ArrayList<>();
        double dcL=0,dcR=0,sum=0,peak=0; int count=0;
        for(int i=0;i<frames;i++){
            double l=finite(track.stereo[i*2]),r=finite(track.stereo[i*2+1]);
            peak=Math.max(peak,Math.max(Math.abs(l),Math.abs(r)));
            dcL+=2e-4*(l-dcL);dcR+=2e-4*(r-dcR);
            sum+=((l-dcL)*(l-dcL)+(r-dcR)*(r-dcR))*.5;
            if(++count==block){energy.add(sum/count);sum=0;count=0;}
        }
        if(count>sr*.1) energy.add(sum/count);
        List<Double> audible=new ArrayList<>();for(double x:energy)if(x>1e-6)audible.add(x);
        double mean=audible.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        List<Double> gated=new ArrayList<>();for(double x:audible)if(x>=mean*.1)gated.add(x);
        double power=gated.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return new Level(power>0?10*Math.log10(power):-120,peak,gated.size()>=2&&power>1e-6,gated.size()*.4);
    }

    public static Double targetGain(Level master, Level slave, double masterGain){
        if(master==null||slave==null||!master.valid||!slave.valid||masterGain<.01)return null;
        double requested=masterGain*Math.pow(10,(master.db-slave.db)/20);
        return clamp(requested,.01,Math.min(MAXIMUM,2/Math.max(1e-3,slave.peak)));
    }
    private static double finite(float x){return Float.isFinite(x)?x:0;}
    private static double clamp(double x,double a,double b){return Math.max(a,Math.min(b,x));}
}
