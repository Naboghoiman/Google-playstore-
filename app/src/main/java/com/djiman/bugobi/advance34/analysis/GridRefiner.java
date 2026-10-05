package com.djiman.bugobi.advance34.analysis;

import com.djiman.bugobi.advance34.model.AnalysisResult;
import com.djiman.bugobi.advance34.model.PcmTrack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Direct native port of grid-refine.js from the supplied APK. */
public final class GridRefiner {
    private GridRefiner() {}
    private record W(double value,double weight) {}
    private record Peak(double time,double weight,int index) {}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private static double mod(double x,double n){return (x%n+n)%n;}
    private static double wrap(double x,double n){return mod(x+n/2,n)-n/2;}
    private static double weightedMedian(List<W> values){
        if(values.isEmpty())return 0;values.sort(Comparator.comparingDouble(W::value));double total=0;for(W w:values)total+=w.weight;double acc=0;for(W w:values){acc+=w.weight;if(acc>=total/2)return w.value;}return values.get(values.size()-1).value;
    }
    public static AnalysisResult refine(PcmTrack track, AnalysisResult original){
        int sr=track.sampleRate;float[] stereo=track.stereo;double bpm=original.bpm;if(stereo.length/2<sr*8||!Double.isFinite(bpm)||bpm<35||bpm>300)return original;
        final int block=64,frames=stereo.length/2,n=frames/block;float[] power=new float[n],novel=new float[n];double alpha=1-Math.exp(-2*Math.PI*180/sr);double lp=0,last=0,sumNovel=0;
        for(int t=0;t<n;t++){double sum=0;for(int s=0;s<block;s++){int i=t*block+s;double x=(stereo[i*2]+stereo[i*2+1])*.5;lp+=alpha*(x-lp);sum+=lp*lp;}double rms=Math.sqrt(sum/block);power[t]=(float)rms;novel[t]=(float)Math.max(0,rms-last);last=rms;sumNovel+=novel[t];}
        double threshold=sumNovel/n*.7;ArrayList<Peak> peaks=new ArrayList<>();int radius=Math.max(1,(int)Math.round(.025*sr/block));
        for(int t=radius;t<n-radius;t++){double v=novel[t];if(v<=threshold||v<1e-5)continue;boolean best=true;for(int q=1;q<=radius;q++)if(novel[t-q]>v||novel[t+q]>=v){best=false;break;}if(!best)continue;double left=novel[t-1],right=novel[t+1],den=2*(2*v-left-right),delta=den>0?clamp((right-left)/den,-.5,.5):0;Peak p=new Peak((t+.5+delta)*block/(double)sr,Math.sqrt(v),t);if(!peaks.isEmpty()&&p.time-peaks.get(peaks.size()-1).time<.11){if(p.weight>peaks.get(peaks.size()-1).weight)peaks.set(peaks.size()-1,p);}else peaks.add(p);}
        if(peaks.size()<24)return original;double expected=60/bpm;ArrayList<W> periods=new ArrayList<>();
        for(int i=0;i<peaks.size();i++)for(int j=i+1;j<peaks.size();j++){double dt=peaks.get(j).time-peaks.get(i).time;if(dt>expected*16.2)break;long beats=Math.round(dt/expected);if(beats<4||beats>16)continue;double per=dt/beats;if(Math.abs(per/expected-1)<=.015)periods.add(new W(per,Math.min(peaks.get(i).weight,peaks.get(j).weight)));}
        if(periods.size()<40)return original;double period=weightedMedian(periods),offset=0;double[] bins=new double[256];for(Peak peak:peaks){int bin=(int)Math.round(mod(peak.time,period)/period*256)%256;for(int r=-3;r<=3;r++)bins[(bin+r+256)%256]+=peak.weight*(4-Math.abs(r));}int bi=0;for(int i=1;i<bins.length;i++)if(bins[i]>bins[bi])bi=i;offset=bi/256.0*period;
        ArrayList<Peak> inliers=new ArrayList<>();double rms=Double.POSITIVE_INFINITY;
        for(int pass=0;pass<5;pass++){
            ArrayList<Peak> candidates=new ArrayList<>();for(Peak p:peaks)if(Math.abs(wrap(p.time-offset,period))<period*.16)candidates.add(p);if(candidates.size()<20)return original;
            ArrayList<W> residual=new ArrayList<>();for(Peak p:candidates)residual.add(new W(Math.abs(wrap(p.time-offset,period)),p.weight));double limit=Math.max(.006,Math.min(period*.16,weightedMedian(residual)*3.5));inliers.clear();for(Peak p:candidates)if(Math.abs(wrap(p.time-offset,period))<=limit)inliers.add(p);
            double sw=0,sx=0,sy=0;for(Peak p:inliers){double beat=Math.rint((p.time-offset)/period);sw+=p.weight;sx+=beat*p.weight;sy+=p.time*p.weight;}sx/=sw;sy/=sw;double xx=0,xy=0;for(Peak p:inliers){double beat=Math.rint((p.time-offset)/period),dx=beat-sx;xx+=p.weight*dx*dx;xy+=p.weight*dx*(p.time-sy);}if(xx<=0)return original;double next=xy/xx;if(Math.abs(next/expected-1)>.015)return original;offset=sy-sx*next;period=next;double err=0;for(Peak p:inliers)err+=p.weight*Math.pow(wrap(p.time-offset,period),2);rms=Math.sqrt(err/sw);
        }
        double duration=frames/(double)sr,coverage=(inliers.get(inliers.size()-1).time-inliers.get(0).time)/duration;double confidence=clamp(inliers.size()/(double)peaks.size()*2,0,1)*.45+clamp(coverage,0,1)*.30+clamp(1-rms/.03,0,1)*.25;if(inliers.size()<24||coverage<.45||rms>.025||confidence<.65)return original;
        ArrayList<W> attackOffsets=new ArrayList<>();int before=(int)Math.ceil(.035*sr/block),after=(int)Math.ceil(.01*sr/block);
        for(Peak peak:inliers){int start=Math.max(0,peak.index-before),end=Math.min(n-1,peak.index+after);double base=Double.POSITIVE_INFINITY,hi=0;for(int i=start;i<=peak.index;i++)base=Math.min(base,power[i]);for(int i=peak.index;i<=end;i++)hi=Math.max(hi,power[i]);double level=base+(hi-base)*.15;int at=peak.index;while(at>start&&power[at-1]>level)at--;if(at<=start||power[at]<=power[at-1])continue;double frac=clamp((level-power[at-1])/(power[at]-power[at-1]),0,1),time=(at-.5+frac)*block/(double)sr;attackOffsets.add(new W(wrap(time-offset,period),peak.weight));}
        double attack=attackOffsets.size()>inliers.size()*.5?weightedMedian(attackOffsets):0;
        AnalysisResult out=original.copy();out.bpm=60/period;out.beatgridOffset=mod(offset+attack,period);out.bpmConfidence=confidence;out.phaseConfidence=confidence;out.gridFitRmsMs=rms*1000;out.gridReason="refined-steady-grid";return out;
    }
}
