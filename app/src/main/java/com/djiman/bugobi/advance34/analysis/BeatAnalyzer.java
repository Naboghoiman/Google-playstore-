package com.djiman.bugobi.advance34.analysis;

import com.djiman.bugobi.advance34.model.AnalysisResult;
import com.djiman.bugobi.advance34.model.PcmTrack;

import java.util.*;

/**
 * Native Java port of the multiband grid analysis embedded in the supplied 3.4 APK.
 * No WebAudio/Worker/JavaScript is used. The same tempo range, tempo-family
 * normalization, multisection voting, phase folding and grid fitting rules are kept.
 */
public final class BeatAnalyzer {
    public static final String VERSION = "iman-multiband-grid-2-native";

    private BeatAnalyzer() {}

    private static double clamp(double x, double a, double b) { return Math.max(a, Math.min(b, x)); }
    private static double mod(double x, double n) { return (x % n + n) % n; }
    private static double wrap(double x, double n) { return mod(x + n / 2.0, n) - n / 2.0; }

    private static double median(double[] in) {
        if (in.length == 0) return 0;
        double[] s = in.clone(); Arrays.sort(s); return s[s.length / 2];
    }
    private static double median(List<Double> in) {
        if (in.isEmpty()) return 0;
        double[] a = new double[in.size()];
        for (int i=0;i<a.length;i++) a[i]=in.get(i);
        return median(a);
    }

    private static final class Peak {
        int frame; double time, weight, bass;
        Peak(int frame, double time, double weight, double bass){this.frame=frame;this.time=time;this.weight=weight;this.bass=bass;}
    }
    private static final class Features {
        double duration, fps, rms;
        float[] onset, bass;
        float[][] energy;
        List<Peak> peaks;
        float[] overviewLow, overviewMid, overviewHigh, wavePeaks, detailLow, detailMid, detailHigh;
    }
    private static final class Candidate {
        double bpm, score, support; int section;
        Candidate(double bpm,double score,int section){this.bpm=bpm;this.score=score;this.section=section;}
    }
    private static final class Cluster {
        double bpm, score, support; List<Candidate> votes = new ArrayList<>();
    }
    private static final class Phase { double score, offset; Phase(double s,double o){score=s;offset=o;} }
    private static class Fit {
        double period, offset, rms, coverage; int inliers;
        Fit(double period,double offset,double rms,double coverage,int inliers){this.period=period;this.offset=offset;this.rms=rms;this.coverage=coverage;this.inliers=inliers;}
    }
    private static final class Winner extends Fit {
        double score; Cluster candidate;
        Winner(Fit f,double score,Cluster c){super(f.period,f.offset,f.rms,f.coverage,f.inliers);this.score=score;this.candidate=c;}
    }

    public static AnalysisResult analyze(PcmTrack track) {
        long started = System.currentTimeMillis();
        Features f = features(track);
        List<Cluster> candidates = tempoCandidates(f);
        AnalysisResult out = new AnalysisResult();
        out.overviewLow=f.overviewLow; out.overviewMid=f.overviewMid; out.overviewHigh=f.overviewHigh; out.peaks=f.wavePeaks;
        out.detailLow=f.detailLow; out.detailMid=f.detailMid; out.detailHigh=f.detailHigh;
        if (f.duration < 2 || f.rms < 1e-5 || f.peaks.size() < 5 || candidates.isEmpty()) return out;

        Winner winner = null;
        for (Cluster candidate : candidates) {
            double period = 60.0 / candidate.bpm;
            Phase best = new Phase(0,0); double winningPeriod = period;
            double range = period * 0.018;
            double step = Math.max(12e-6, period * period / Math.max(20.0, f.duration) / 24.0);
            List<Peak> sampled = f.peaks;
            if (sampled.size() > 7000) {
                ArrayList<Peak> half = new ArrayList<>(sampled.size()/2+1);
                for(int i=0;i<sampled.size();i+=2) half.add(sampled.get(i));
                sampled=half;
            }
            for(double p=period-range; p<=period+range; p+=step){
                Phase r=phaseScore(sampled,p);
                if(r.score>best.score){best=r;winningPeriod=p;}
            }
            Fit fit=fitGrid(f.peaks,winningPeriod,best.offset,f.duration);
            double score=best.score*(0.65+0.35*candidate.score/Math.max(1e-3,candidates.get(0).score));
            if(winner==null||score>winner.score) winner=new Winner(fit,score,candidate);
        }
        if(winner==null) return out;

        List<Double> path=dynamicPath(f,winner.period,winner.offset);
        ArrayList<Peak> pathPeaks=new ArrayList<>(path.size());
        for(double time:path){
            int idx=(int)Math.round(time*f.fps-0.5);
            double w=.5+Math.min(2, idx>=0&&idx<f.onset.length?f.onset[idx]:0);
            pathPeaks.add(new Peak(idx,time,w,0));
        }
        Fit pathFit=fitGrid(pathPeaks,winner.period,winner.offset,f.duration);
        if(path.size()>0 && pathFit.inliers/(double)path.size()>.82 && pathFit.rms<.016 && pathFit.coverage>.65){
            winner.period=pathFit.period; winner.offset=pathFit.offset; winner.rms=pathFit.rms; winner.coverage=pathFit.coverage; winner.inliers=pathFit.inliers;
        }
        ArrayList<Double> reliable=new ArrayList<>();
        for(double t:path){int idx=(int)Math.round(t*f.fps-0.5); if(idx>=0&&idx<f.onset.length&&f.onset[idx]>1.2) reliable.add(t);}
        double attack=attackOffset(f,reliable);
        int alignedCount=0;
        for(double t:path) if(Math.abs(wrap(t-winner.offset,winner.period))<.025) alignedCount++;
        double aligned=alignedCount/(double)Math.max(1,path.size());
        boolean steady=aligned>.78&&winner.rms<.02&&winner.coverage>.65;
        Drift drift=tempoChangeEvidence(f,path,winner.period,steady,reliable.size()/(double)Math.max(1,path.size()));
        double offset=mod(winner.offset+attack,winner.period), bpm=60.0/winner.period;
        double confidence=clamp(.25*winner.candidate.support+.4*aligned+.35*Math.min(1,reliable.size()/(double)Math.max(1,path.size())),0,1);
        List<Double> beatTimes=new ArrayList<>();
        if(drift.verified && path.size()>=3){
            ArrayList<Double> smooth=new ArrayList<>(path.size());
            for(int i=0;i<path.size();i++){
                double sw=0,sx=0,sy=0,sxx=0,sxy=0;
                for(int j=Math.max(0,i-4);j<=Math.min(path.size()-1,i+4);j++){
                    double x=j-i; int idx=(int)Math.round(path.get(j)*f.fps-.5);
                    double onset=idx>=0&&idx<f.onset.length?f.onset[idx]:0;
                    double w=(5-Math.abs(x))*(.35+Math.min(1,onset/3.0));
                    sw+=w;sx+=w*x;sy+=w*path.get(j);sxx+=w*x*x;sxy+=w*x*path.get(j);
                }
                double den=sw*sxx-sx*sx;
                smooth.add((den!=0?(sy*sxx-sx*sxy)/den:path.get(i))+attack);
            }
            double firstPeriod=smooth.get(1)-smooth.get(0), lastPeriod=smooth.get(smooth.size()-1)-smooth.get(smooth.size()-2);
            while(smooth.get(0)>firstPeriod) smooth.add(0,smooth.get(0)-firstPeriod);
            if(smooth.get(0)<0) smooth.remove(0);
            while(smooth.get(smooth.size()-1)<f.duration) smooth.add(smooth.get(smooth.size()-1)+lastPeriod);
            boolean valid=true;
            for(int i=1;i<smooth.size();i++){double d=smooth.get(i)-smooth.get(i-1);if(!(d>winner.period*.65&&d<winner.period*1.45)){valid=false;break;}}
            if(valid){
                beatTimes=smooth; offset=smooth.get(0);
                ArrayList<Double> ds=new ArrayList<>(); for(int i=1;i<smooth.size();i++)ds.add(smooth.get(i)-smooth.get(i-1));
                bpm=60.0/median(ds);
            }
        }
        out.bpm=bpm; out.beatgridOffset=offset; out.bpmConfidence=confidence; out.phaseConfidence=confidence;
        out.beatTimes=beatTimes; out.gridVariable=!beatTimes.isEmpty(); out.gridFitRmsMs=winner.rms*1000;
        out.gridReason=steady?"steady-grid":(!beatTimes.isEmpty()?"tracked-tempo":"estimated-grid");
        return out;
    }

    private static Features features(PcmTrack track){
        float[] stereo=track.stereo; int sr=track.sampleRate; int frames=stereo.length/2;
        double duration=frames/(double)sr;
        int stride=Math.max(1,(int)Math.floor(sr/12000.0)); double rate=sr/(double)stride;
        int hop=Math.max(1,(int)Math.round(rate/200.0)); double fps=rate/hop;
        int n=(int)Math.ceil(frames/(double)stride/hop), bands=6;
        float[][] energy=new float[bands][n];
        double[] coeff=new double[]{90,220,550,1400,3400}; for(int i=0;i<coeff.length;i++) coeff[i]=1-Math.exp(-2*Math.PI*coeff[i]/rate);
        double[] lp=new double[5], sums=new double[bands];
        int detail=Math.max(1,Math.min(6000,(int)Math.ceil(duration*50))), overview=800;
        float[] ovL=new float[overview],ovM=new float[overview],ovH=new float[overview],detL=new float[detail],detM=new float[detail],detH=new float[detail],wavePeaks=new float[overview];
        int frame=0,count=0; double total=0;
        for(int fi=0;fi<frames;fi+=stride){
            float l=stereo[fi*2], r=stereo[fi*2+1];
            double x=Math.signum(Math.abs(l)>=Math.abs(r)?l:r)*Math.sqrt((l*l+r*r)*.5);
            double previous=0;
            for(int b=0;b<5;b++){ lp[b]+=coeff[b]*(x-lp[b]); double v=lp[b]-previous; sums[b]+=v*v; previous=lp[b]; }
            sums[5]+=(x-previous)*(x-previous); total+=x*x; count++;
            int oi=Math.min(799,(int)Math.floor(fi/(double)frames*800)); int di=Math.min(detail-1,(int)Math.floor(fi/(double)frames*detail));
            float lo=(float)Math.abs(lp[1]), mi=(float)Math.abs(lp[4]-lp[1]), hi=(float)Math.abs(x-lp[4]);
            ovL[oi]=Math.max(ovL[oi],Math.min(1,lo*1.6f)); ovM[oi]=Math.max(ovM[oi],Math.min(1,mi*1.4f)); ovH[oi]=Math.max(ovH[oi],Math.min(1,hi*1.8f)); wavePeaks[oi]=Math.max(wavePeaks[oi],Math.min(1,(float)Math.abs(x)));
            detL[di]=Math.max(detL[di],Math.min(1,lo*1.6f)); detM[di]=Math.max(detM[di],Math.min(1,mi*1.4f)); detH[di]=Math.max(detH[di],Math.min(1,hi*1.8f));
            if(count==hop||fi+stride>=frames){ for(int b=0;b<bands;b++){energy[b][frame]=(float)Math.sqrt(sums[b]/count);sums[b]=0;} frame++;count=0; }
        }
        float[] onset=new float[n],bass=new float[n]; float[][] whitened=new float[bands][n];
        for(int b=0;b<bands;b++){
            float[] flux=new float[n],e=energy[b]; double slow=0;
            for(int i=0;i<n;i++){slow+=.006*(e[i]-slow);flux[i]=(float)(Math.max(0,e[i]-(i>0?e[i-1]:0))/(slow+.002));}
            double[] prefix=new double[n+1]; for(int i=0;i<n;i++)prefix[i+1]=prefix[i]+flux[i]; int w=(int)Math.round(fps*2);
            for(int i=0;i<n;i++){int a=Math.max(0,i-w),z=Math.min(n,i+w+1);double mean=(prefix[z]-prefix[a])/(z-a);whitened[b][i]=(float)Math.min(5,Math.max(0,flux[i]-.45*mean)/(mean+.08));}
        }
        double[] weights={1.8,1.4,.8,.65,.5,.4};
        for(int i=0;i<n;i++){for(int b=0;b<bands;b++)onset[i]+=whitened[b][i]*weights[b];bass[i]=whitened[0][i]*.8f+whitened[1][i]+whitened[2][i]*.45f;}
        double lowMean=0;for(float v:energy[0])lowMean+=v;lowMean/=Math.max(1,n);float[] smooth=new float[n];
        for(int i=1;i<n-1;i++){double sal=.6+Math.min(2.4,Math.max(energy[0][i],energy[0][i+1])/(lowMean*7+1e-5));smooth[i]=(float)((onset[i-1]+2*onset[i]+onset[i+1])*.25*sal);}
        ArrayList<Peak> peaks=new ArrayList<>(); int gap=Math.max(1,(int)Math.round(fps*.045));
        for(int i=2;i<n-2;i++){
            if(smooth[i]<.7||smooth[i]<smooth[i-1]||smooth[i]<=smooth[i+1])continue;
            Peak p=new Peak(i,(i+.5)/fps,Math.sqrt(smooth[i]),bass[i]);
            if(!peaks.isEmpty()&&i-peaks.get(peaks.size()-1).frame<gap){if(p.weight>peaks.get(peaks.size()-1).weight)peaks.set(peaks.size()-1,p);}else peaks.add(p);
        }
        Features f=new Features();f.duration=duration;f.fps=fps;f.onset=smooth;f.bass=bass;f.energy=energy;f.peaks=peaks;f.overviewLow=ovL;f.overviewMid=ovM;f.overviewHigh=ovH;f.wavePeaks=wavePeaks;f.detailLow=detL;f.detailMid=detM;f.detailHigh=detH;f.rms=Math.sqrt(total/Math.max(1,Math.ceil(frames/(double)stride)));return f;
    }

    private static List<Cluster> tempoCandidates(Features f){
        float[] onset=f.onset; double fps=f.fps,duration=f.duration; int lo=(int)Math.floor(fps*60/300), hi=(int)Math.ceil(fps*60/45); ArrayList<Candidate> votes=new ArrayList<>();
        int sections=Math.min(8,Math.max(1,(int)Math.floor(duration/12)));
        for(int section=0;section<=sections;section++){
            int start=section==sections?0:(int)Math.floor(section*onset.length/(double)sections), end=section==sections?onset.length:(int)Math.floor((section+1)*onset.length/(double)sections);
            double[] corr=new double[hi*3+2];
            for(int lag=lo;lag<=hi*3;lag++){double ab=0,aa=0,bb=0;int step=section==sections?3:2;for(int i=start;i+lag<end;i+=step){double a=onset[i],b=onset[i+lag];ab+=a*b;aa+=a*a;bb+=b*b;}corr[lag]=ab/Math.sqrt(aa*bb+1e-12);}
            ArrayList<Candidate> cs=new ArrayList<>();
            for(int lag=lo+1;lag<hi;lag++){
                if(corr[lag]<corr[lag-1]||corr[lag]<=corr[lag+1])continue; double den=2*(2*corr[lag]-corr[lag-1]-corr[lag+1]); double delta=den!=0?clamp((corr[lag+1]-corr[lag-1])/den,-.5,.5):0; double bpm=fps*60/(lag+delta); while(bpm<72)bpm*=2;while(bpm>175)bpm/=2; double score=(corr[lag]+.45*corr[lag*2]+.2*corr[lag*3])*(.9+.1*Math.exp(-.5*Math.pow(Math.log(bpm/115)/Math.log(2)/.65,2))); cs.add(new Candidate(bpm,score,section));
            }
            cs.sort((a,b)->Double.compare(b.score,a.score)); for(int i=0;i<Math.min(6,cs.size());i++)votes.add(cs.get(i));
        }
        votes.sort((a,b)->Double.compare(b.score,a.score)); ArrayList<Cluster> clusters=new ArrayList<>();
        for(Candidate vote:votes){Cluster g=null;for(Cluster c:clusters)if(Math.abs(Math.log(c.bpm/vote.bpm))<.018){g=c;break;}if(g==null){g=new Cluster();g.bpm=vote.bpm;clusters.add(g);}g.votes.add(vote);double sum=0,weighted=0;for(Candidate v:g.votes){sum+=v.score;weighted+=v.bpm*v.score;}g.bpm=weighted/(sum==0?1:sum);}
        for(Cluster c:clusters){HashMap<Integer,Double> best=new HashMap<>();for(Candidate v:c.votes)best.put(v.section,Math.max(best.getOrDefault(v.section,0.0),v.score));double s=0;for(double v:best.values())s+=v;c.score=s/(sections+1);c.support=best.size()/(double)(sections+1);}
        clusters.sort((a,b)->Double.compare(b.score,a.score)); return clusters.subList(0,Math.min(5,clusters.size()));
    }

    private static Phase phaseScore(List<Peak> peaks,double period){double[] bins=new double[96];for(Peak p:peaks){double q=mod(p.time,period)/period*96;int i=(int)Math.floor(q);double fr=q-i,w=p.weight*(1+Math.min(2,p.bass)*.12);bins[i]+=w*(1-fr);bins[(i+1)%96]+=w*fr;}double best=0;int index=0;for(int i=0;i<96;i++){double s=bins[i]+.8*(bins[(i+1)%96]+bins[(i+95)%96])+.35*(bins[(i+2)%96]+bins[(i+94)%96]);if(s>best){best=s;index=i;}}return new Phase(best,index/96.0*period);}

    private static Fit fitGrid(List<Peak> peaks,double period,double offset,double span){List<Peak> chosen=Collections.emptyList();double rms=1;for(int pass=0;pass<5;pass++){double limit=pass<2?period*.14:Math.max(.014,Math.min(period*.11,rms*2.8));ArrayList<Peak> c=new ArrayList<>();for(Peak p:peaks)if(Math.abs(wrap(p.time-offset,period))<limit)c.add(p);chosen=c;if(chosen.size()<8)break;double sw=0,sx=0,sy=0;for(Peak p:chosen){double x=Math.rint((p.time-offset)/period);sw+=p.weight;sx+=x*p.weight;sy+=p.time*p.weight;}sx/=sw;sy/=sw;double xx=0,xy=0;for(Peak p:chosen){double x=Math.rint((p.time-offset)/period)-sx;xx+=x*x*p.weight;xy+=x*(p.time-sy)*p.weight;}if(xx<1e-6)break;double next=xy/xx;if(Math.abs(next/period-1)>.018)break;period=next;offset=sy-sx*period;double err=0;for(Peak p:chosen)err+=p.weight*Math.pow(wrap(p.time-offset,period),2);rms=Math.sqrt(err/sw);}double coverage=chosen.isEmpty()?0:clamp((chosen.get(chosen.size()-1).time-chosen.get(0).time)/span,0,1);return new Fit(period,mod(offset,period),rms,coverage,chosen.size());}

    private static List<Double> dynamicPath(Features f,double period,double phase){float[] onset=f.onset;double fps=f.fps;int n=onset.length;double p=period*fps;int lo=(int)Math.floor(p*.78),hi=(int)Math.ceil(p*1.23);float[] cumulative=new float[n];int[] previous=new int[n];Arrays.fill(previous,-1);float[] penalties=new float[hi+1];for(int d=lo;d<=hi;d++)penalties[d]=(float)(110*Math.pow(Math.log(d/p),2));for(int i=0;i<n;i++){double best=0;int at=-1;for(int d=lo;d<=hi&&d<=i;d++){double score=cumulative[i-d]-penalties[d];if(score>best){best=score;at=i-d;}}double phaseCost=.2*Math.pow(wrap((i+.5)/fps-phase,period)/period,2);cumulative[i]=(float)(best+onset[i]-phaseCost);previous[i]=at;}int end=n-1;for(int i=Math.max(0,n-(int)Math.ceil(p*2));i<n;i++)if(cumulative[i]>cumulative[end])end=i;ArrayList<Double> rev=new ArrayList<>();for(int i=end;i>=0;i=previous[i]){rev.add((i+.5)/fps);if(previous[i]==i||previous[i]<0)break;}Collections.reverse(rev);return rev;}

    private static double attackOffset(Features f,List<Double> times){ArrayList<Double> corrections=new ArrayList<>();for(double time:times){int i=(int)Math.round(time*f.fps-.5);if(i<7||i>=f.onset.length-3)continue;int band=0;double novelty=0;for(int b=0;b<6;b++){float[] e=f.energy[b];double v=(e[i]-e[i-2])/(e[i]+.003);if(v>novelty){novelty=v;band=b;}}if(novelty<.35)continue;float[] e=f.energy[band];int start=i-6;double base=Double.POSITIVE_INFINITY;for(int j=start;j<i;j++)base=Math.min(base,e[j]);double peak=Math.max(e[i],e[i+1]),level=base+(peak-base)*.15;int a=i;while(a>start&&e[a-1]>level)a--;if(a==start)continue;double frac=clamp((level-e[a-1])/(e[a]-e[a-1]+1e-9),0,1);corrections.add((a-.5+frac)/f.fps-time);}return clamp(median(corrections),-.03,0);}

    private static final class Drift {boolean verified;int windows,reliableWindows;double range,localRmsMs,onsetSupport;}
    private static Drift tempoChangeEvidence(Features f,List<Double> path,double period,boolean steady,double reliableFraction){ArrayList<double[]> windows=new ArrayList<>();for(int start=2;start+16<=path.size()-2;start+=16){double sy=0,sxy=0,center=7.5,xx=340;for(int j=0;j<16;j++){sy+=path.get(start+j);sxy+=(j-center)*path.get(start+j);}double slope=sxy/xx,intercept=sy/16,res=0;int support=0;for(int j=0;j<16;j++){res+=Math.pow(path.get(start+j)-intercept-slope*(j-center),2);int idx=(int)Math.round(path.get(start+j)*f.fps-.5);if(idx>=0&&idx<f.onset.length&&f.onset[idx]>1.2)support++;}windows.add(new double[]{slope,Math.sqrt(res/16),support/16.0});}ArrayList<double[]> measured=new ArrayList<>();for(double[] w:windows)if(w[1]<.02&&w[2]>=.65&&w[0]>period*.78&&w[0]<period*1.23)measured.add(w);ArrayList<Double> ps=new ArrayList<>();for(double[] w:measured)ps.add(w[0]);Collections.sort(ps);double range=ps.size()>2?(ps.get((int)Math.floor((ps.size()-1)*.8))-ps.get((int)Math.floor((ps.size()-1)*.2)))/median(ps):0;Drift d=new Drift();d.verified=!steady&&measured.size()>=3&&measured.size()>=windows.size()*.75&&reliableFraction>=.65&&range>.012;d.windows=windows.size();d.reliableWindows=measured.size();d.range=range*100;ArrayList<Double> rms=new ArrayList<>();for(double[] w:measured)rms.add(w[1]);d.localRmsMs=median(rms)*1000;d.onsetSupport=reliableFraction;return d;}
}
