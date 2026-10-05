package com.djiman.bugobi.advance34.analysis;

import com.djiman.bugobi.advance34.model.AnalysisResult;
import java.util.List;

/** Native equivalent of sync-upgrade.js beatAt/timeAt/bpmAt/nextTime. */
public final class BeatGridMath {
    private BeatGridMath() {}
    public static double beatAt(AnalysisResult g,double time){
        List<Double> a=g==null?null:g.beatTimes;if(a==null||a.size()<2)return (time-(g==null?0:g.beatgridOffset))*(g==null?120:g.bpm)/60.0;
        int lo=0,hi=a.size()-1;if(time<=a.get(0)){double p=a.get(1)-a.get(0);return (time-a.get(0))/p;}if(time>=a.get(hi)){double p=a.get(hi)-a.get(hi-1);return hi+(time-a.get(hi))/p;}
        while(hi-lo>1){int mid=(lo+hi)>>>1;if(a.get(mid)<=time)lo=mid;else hi=mid;}return lo+(time-a.get(lo))/(a.get(lo+1)-a.get(lo));
    }
    public static double timeAt(AnalysisResult g,double beat){
        List<Double> a=g==null?null:g.beatTimes;if(a==null||a.size()<2)return (g==null?0:g.beatgridOffset)+beat*60.0/(g==null?120:g.bpm);
        if(beat<0){double p=a.get(1)-a.get(0);return a.get(0)+beat*p;}int last=a.size()-1;if(beat>=last){double p=a.get(last)-a.get(last-1);return a.get(last)+(beat-last)*p;}int i=Math.max(0,Math.min(a.size()-2,(int)Math.floor(beat)));return a.get(i)+(beat-i)*(a.get(i+1)-a.get(i));
    }
    public static double bpmAt(AnalysisResult g,double time){List<Double>a=g==null?null:g.beatTimes;if(a==null||a.size()<2)return g==null?120:g.bpm;double b=beatAt(g,time);int i=Math.max(0,Math.min(a.size()-2,(int)Math.floor(b)));return 60.0/(a.get(i+1)-a.get(i));}
    public static double nextTime(AnalysisResult g,double time,double multiple){double b=beatAt(g,time),m=Math.max(1e-6,multiple);return timeAt(g,Math.ceil(b/m-1e-9)*m);}
}
