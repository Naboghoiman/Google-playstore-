package com.djiman.bugobi.advance34;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;

import com.djiman.bugobi.advance34.analysis.AutoTrim;
import com.djiman.bugobi.advance34.analysis.BeatAnalyzer;
import com.djiman.bugobi.advance34.analysis.GridRefiner;
import com.djiman.bugobi.advance34.analysis.BeatGridMath;
import com.djiman.bugobi.advance34.audio.NativeDjEngine;
import com.djiman.bugobi.advance34.audio.AutoScratchController;
import com.djiman.bugobi.advance34.audio.SyncController;
import com.djiman.bugobi.advance34.media.MediaStoreRepository;
import com.djiman.bugobi.advance34.media.PcmDecoder;
import com.djiman.bugobi.advance34.model.AnalysisResult;
import com.djiman.bugobi.advance34.model.DeckState;
import com.djiman.bugobi.advance34.model.PcmTrack;
import com.djiman.bugobi.advance34.model.TrackInfo;
import com.djiman.bugobi.advance34.rhythm.RhythmController;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Central native application controller. No JavaScript bridge exists in this port. */
public final class DjController implements AutoCloseable {
    public interface Listener {
        default void onDeckLoading(int deck, TrackInfo track) {}
        default void onDeckLoaded(int deck, DeckState state) {}
        default void onDeckError(int deck, TrackInfo track, Throwable error) {}
        default void onStateChanged() {}
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(2, r -> { Thread t=new Thread(r,"djiman-decode");t.setDaemon(true);return t; });
    private final ScheduledExecutorService maintenance = Executors.newSingleThreadScheduledExecutor(r -> {Thread t=new Thread(r,"djiman-maintenance");t.setDaemon(true);return t;});
    public final NativeDjEngine engine = new NativeDjEngine();
    public final DeckState deckA = new DeckState("A");
    public final DeckState deckB = new DeckState("B");
    public final SyncController sync = new SyncController(engine, deckA, deckB);
    public final AutoScratchController autoScratch = new AutoScratchController(engine, deckA, deckB);
    public final MediaStoreRepository library;
    public final RhythmController rhythm;
    private final Map<Integer, AutoTrim.Level> levels = new ConcurrentHashMap<>();
    private volatile Listener listener = new Listener(){};
    private volatile boolean autoTrimEnabled = true;
    private volatile File lastRecording;
    private volatile boolean samplerSync = true;
    private volatile boolean samplerMuted = false;
    private volatile float padVolume = 1.0f;
    private volatile int lastGrooveMaster = -1;
    public volatile float masterTrim = 1.0f;
    public volatile float masterLevel = 1.0f;
    public volatile float masterBalance = 0.0f;
    public volatile float masterFilter = 0.0f;
    public volatile float masterEqLowDb = 0.0f, masterEqMidDb = 0.0f, masterEqHighDb = 0.0f;
    public volatile boolean masterFxEnabled = false;
    public volatile int masterFxType = NativeDjEngine.FX_FLANGER;
    public volatile float masterFxTime = 0.5f, masterFxDepth = 0.5f, masterFxLevel = 0.5f;

    // Exact Master FX rack state recovered from the packaged 3.4 UI.
    // Amounts are normalized 0..1; modes map to the option order in the original selectors.
    public final boolean[] rackFxEnabled = new boolean[12];
    public final float[] rackFxAmount = { .22f,.14f,.10f,.50f,.30f,.20f,.40f,.45f,.18f,.35f,.45f,.35f };
    public final int[] rackFxMode = { 0,1,1,0,0,0,0,0,0,0,0,0 };
    public volatile float rackFxWet = .38f;
    public volatile String rackFxPreset = "Club Standard";
    private final double[][] hotCues = {{Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN},{Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN}};
    private final boolean[] cuePreviewHeld = {false,false};
    private final boolean[] cuePlayLatched = {false,false};
    private static final String[] FACTORY_SAMPLE_NAMES = {
            "yoyoo","What A Way A Boy Like Me Bad","wa wa wa wa wheel","Vybz Kartel Intro2",
            "Vybz Kartel Intro1","ttulu","thitima","Sound The Big Ting Dem",
            "Nico Bam Bam Horn","sirenn 1","siren passing","Signal",
            "Sfx4","Reggae Air horn 3","pullll up weeeeh","Pull Up Bounti",
            "Pull Up Dandara","pista reggatone","Nyash whistle","my gold play me that song again"};
    private final String[] sampleNames = new String[32];
    private final SharedPreferences samplePrefs;
    private final SharedPreferences groovePrefs;
    private final String[] grooveNames = new String[12];
    public volatile float grooveFilter = 0.0f, grooveBassDb = 0.0f, grooveVolume = 0.70f;

    public DjController(Context context) {
        this.context = context.getApplicationContext();
        this.library = new MediaStoreRepository(context);
        this.samplePrefs = this.context.getSharedPreferences("djiman.native.samples", Context.MODE_PRIVATE);
        this.groovePrefs = this.context.getSharedPreferences("djiman.native.groove", Context.MODE_PRIVATE);
        for(int i=0;i<sampleNames.length;i++)sampleNames[i]=i<FACTORY_SAMPLE_NAMES.length?FACTORY_SAMPLE_NAMES[i]:"EMPTY";
        for(int i=0;i<grooveNames.length;i++)grooveNames[i]=groovePrefs.getString("name."+i,"EMPTY");
        grooveFilter=groovePrefs.getFloat("filter",0.0f);grooveBassDb=groovePrefs.getFloat("bass",0.0f);grooveVolume=groovePrefs.getFloat("volume",0.70f);
        engine.start();
        this.rhythm = new RhythmController(this.context, engine, () -> listener.onStateChanged());
        engine.setLooperTone(grooveFilter,grooveBassDb);engine.setLooperGain(grooveVolume);
        applyRackFxPreset("Club Standard");
        maintenance.scheduleAtFixedRate(this::autoTrimTick, 250, 250, TimeUnit.MILLISECONDS);
        preloadFactorySamples();
        restoreGrooveLoops();
    }

    public void setListener(Listener listener) { this.listener = listener == null ? new Listener(){} : listener; }
    public DeckState state(int deck) { return deck == NativeDjEngine.DECK_B ? deckB : deckA; }
    public List<TrackInfo> queryLibrary() { return library.queryAll(); }

    public void loadTrack(int deck, TrackInfo track) {
        DeckState state = state(deck); state.loading = true; state.info = track;
        main.post(() -> listener.onDeckLoading(deck, track));
        io.execute(() -> {
            try {
                PcmTrack pcm = PcmDecoder.decode(context, track.uri);
                AnalysisResult analysis = GridRefiner.refine(pcm, BeatAnalyzer.analyze(pcm));
                AutoTrim.Level level = AutoTrim.measure(pcm);
                engine.loadDeck(deck, pcm);
                double[] beatTimes = new double[analysis.beatTimes == null ? 0 : analysis.beatTimes.size()];
                for (int i = 0; i < beatTimes.length; i++) beatTimes[i] = analysis.beatTimes.get(i);
                engine.setDeckBeatGrid(deck, analysis.bpm, analysis.beatgridOffset, beatTimes);
                state.pcm = null; // native engine owns its copy; avoid retaining a duplicate whole-song PCM array.
                state.analysis = analysis; state.loading = false; state.playing = false; state.playbackRate = 1.0;
                levels.put(deck, level);
                engine.setPlaybackRate(deck, 1.0);
                engine.setKeyLock(deck, state.keyLock);
                engine.setTrim(deck, state.trim);
                main.post(() -> { listener.onDeckLoaded(deck, state); listener.onStateChanged(); });
            } catch (Throwable error) {
                state.loading = false;
                main.post(() -> listener.onDeckError(deck, track, error));
            }
        });
    }

    public void playPause(int deck) {
        DeckState s=state(deck); if(s.info==null||s.analysis==null)return;
        if(cuePreviewHeld[deck] && engine.isPlaying(deck)){
            cuePlayLatched[deck]=true;s.playing=true;listener.onStateChanged();return;
        }
        if(engine.isPlaying(deck)){engine.pause(deck);s.playing=false;}else{engine.play(deck);s.playing=true;}
        listener.onStateChanged();
    }
    public void cue(int deck) { engine.pause(deck); engine.seek(deck, cuePoint(state(deck))); state(deck).playing=false; cuePlayLatched[deck]=false; listener.onStateChanged(); }
    public void cueHeld(int deck, boolean held) {
        if(held){cuePreviewHeld[deck]=true;cuePlayLatched[deck]=false;engine.seek(deck,cuePoint(state(deck)));engine.play(deck);return;}
        cuePreviewHeld[deck]=false;
        if(cuePlayLatched[deck]){state(deck).playing=true;cuePlayLatched[deck]=false;}
        else {engine.pause(deck);engine.seek(deck,cuePoint(state(deck)));state(deck).playing=false;}
        listener.onStateChanged();
    }
    private static double cuePoint(DeckState s){return s.analysis==null?0:Math.max(0,s.analysis.beatgridOffset);}

    public void syncTap(int deck) { sync.syncNow(deck); listener.onStateChanged(); }
    public void syncLock(int deck) { sync.toggleLocked(deck); listener.onStateChanged(); }
    public void setCrossfader(float v) { engine.setCrossfader(v); listener.onStateChanged(); }
    public void setTempo(int deck,double rate){sync.operatorTempoChanged(deck,rate);listener.onStateChanged();}
    public void pitchBend(int deck,double amount){engine.setPitchBend(deck,amount);}
    public void setKeyLock(int deck,boolean on){state(deck).keyLock=on;engine.setKeyLock(deck,on);listener.onStateChanged();}
    public void changeKey(int deck,double semitones){state(deck).keySemitones=Math.max(-12,Math.min(12,semitones));engine.setKeySemitones(deck,state(deck).keySemitones);listener.onStateChanged();}
    public void setTrim(int deck,float v,boolean manual){state(deck).trim=v;engine.setTrim(deck,v);if(manual)levels.remove(deck);listener.onStateChanged();}
    public void setVolume(int deck,float v){DeckState s=state(deck);s.volume=Math.max(0,Math.min(1.5f,v));engine.setVolume(deck,s.volume);listener.onStateChanged();}
    public void setEq(int deck,float low,float mid,float high){DeckState s=state(deck);s.eqLowDb=low;s.eqMidDb=mid;s.eqHighDb=high;engine.setEq(deck,low,mid,high);}
    public void setFilter(int deck,float v){state(deck).filter=v;engine.setFilter(deck,v);}

    public void setMasterTrim(float v){masterTrim=Math.max(0,Math.min(4f,v));engine.setMasterTrim(masterTrim);listener.onStateChanged();}
    public void setMasterLevel(float v){masterLevel=Math.max(0,Math.min(2f,v));engine.setMasterLevel(masterLevel);listener.onStateChanged();}
    public void setMasterBalance(float v){masterBalance=Math.max(-1,Math.min(1f,v));engine.setMasterBalance(masterBalance);listener.onStateChanged();}
    public void setMasterFilter(float v){masterFilter=Math.max(-1,Math.min(1f,v));engine.setMasterFilter(masterFilter);listener.onStateChanged();}
    public void setMasterEq(float low,float mid,float high){masterEqLowDb=Math.max(-26,Math.min(6,low));masterEqMidDb=Math.max(-26,Math.min(6,mid));masterEqHighDb=Math.max(-26,Math.min(6,high));engine.setMasterEq(masterEqLowDb,masterEqMidDb,masterEqHighDb);listener.onStateChanged();}
    public void setMasterFx(boolean enabled,int type,float time,float depth,float level){masterFxEnabled=enabled;masterFxType=Math.max(0,Math.min(2,type));masterFxTime=Math.max(0,Math.min(1f,time));masterFxDepth=Math.max(0,Math.min(1f,depth));masterFxLevel=Math.max(0,Math.min(1f,level));engine.setMasterFx(masterFxType,masterFxEnabled,masterFxTime,masterFxDepth,masterFxLevel);listener.onStateChanged();}
    public void cycleMasterFx(int delta){int t=(masterFxType+delta)%3;if(t<0)t+=3;setMasterFx(masterFxEnabled,t,masterFxTime,masterFxDepth,masterFxLevel);}

    public void setRackFx(int module, boolean enabled, float amount, int mode){
        int m=Math.max(0,Math.min(11,module));
        rackFxEnabled[m]=enabled; rackFxAmount[m]=Math.max(0,Math.min(1f,amount)); rackFxMode[m]=Math.max(0,mode);
        engine.setRackFx(m,rackFxEnabled[m],rackFxAmount[m],rackFxMode[m]); listener.onStateChanged();
    }
    public void toggleRackFx(int module){int m=Math.max(0,Math.min(11,module));setRackFx(m,!rackFxEnabled[m],rackFxAmount[m],rackFxMode[m]);}
    public void setRackFxAmount(int module,float amount){int m=Math.max(0,Math.min(11,module));setRackFx(m,rackFxEnabled[m],amount,rackFxMode[m]);}
    public void cycleRackFxMode(int module,int count){int m=Math.max(0,Math.min(11,module));int c=Math.max(1,count);int mode=(rackFxMode[m]+1)%c;setRackFx(m,rackFxEnabled[m],rackFxAmount[m],mode);}
    public void setRackFxWet(float wet){rackFxWet=Math.max(0,Math.min(1f,wet));engine.setRackFxWet(rackFxWet);listener.onStateChanged();}
    public boolean rackFxAnyEnabled(){for(boolean v:rackFxEnabled)if(v)return true;return false;}
    public void setRackFxMaster(boolean on){
        if(!on){for(int i=0;i<12;i++)setRackFx(i,false,rackFxAmount[i],rackFxMode[i]);}
        else applyRackFxPreset(rackFxPreset==null?"Club Standard":rackFxPreset);
    }
    public void cycleRackFxPreset(int delta){
        String[] p={"Club Standard","Vocal Clean","Wide Hall","Dry Tight"};int ix=0;for(int i=0;i<p.length;i++)if(p[i].equals(rackFxPreset)){ix=i;break;}ix=(ix+delta)%p.length;if(ix<0)ix+=p.length;applyRackFxPreset(p[ix]);
    }
    public void applyRackFxPreset(String preset){
        for(int i=0;i<12;i++){rackFxEnabled[i]=false;}
        rackFxPreset=preset;
        if("Vocal Clean".equals(preset)){
            rackFxEnabled[0]=true;rackFxAmount[0]=.18f;rackFxMode[0]=2;
            rackFxEnabled[7]=true;rackFxAmount[7]=.45f;rackFxMode[7]=0;
            rackFxEnabled[8]=true;rackFxAmount[8]=.18f;rackFxMode[8]=0;
            rackFxEnabled[9]=true;rackFxAmount[9]=.12f;rackFxMode[9]=1;rackFxWet=.28f;
        }else if("Wide Hall".equals(preset)){
            rackFxEnabled[0]=true;rackFxAmount[0]=.42f;rackFxMode[0]=0;
            rackFxEnabled[3]=true;rackFxAmount[3]=.30f;rackFxMode[3]=2;
            rackFxEnabled[5]=true;rackFxAmount[5]=.16f;rackFxMode[5]=0;
            rackFxEnabled[8]=true;rackFxAmount[8]=.18f;rackFxMode[8]=0;
            rackFxEnabled[10]=true;rackFxAmount[10]=.45f;rackFxMode[10]=0;rackFxWet=.48f;
        }else if("Dry Tight".equals(preset)){
            rackFxEnabled[8]=true;rackFxAmount[8]=.18f;rackFxMode[8]=0;rackFxWet=.25f;
        }else{
            rackFxPreset="Club Standard";rackFxEnabled[0]=true;rackFxAmount[0]=.22f;rackFxMode[0]=0;
            rackFxEnabled[1]=true;rackFxAmount[1]=.14f;rackFxMode[1]=1;
            rackFxEnabled[2]=true;rackFxAmount[2]=.10f;rackFxMode[2]=1;
            rackFxEnabled[7]=true;rackFxAmount[7]=.45f;rackFxMode[7]=0;
            rackFxEnabled[8]=true;rackFxAmount[8]=.18f;rackFxMode[8]=0;rackFxWet=.38f;
        }
        engine.setRackFxWet(rackFxWet);
        for(int i=0;i<12;i++)engine.setRackFx(i,rackFxEnabled[i],rackFxAmount[i],rackFxMode[i]);
        listener.onStateChanged();
    }

    public void setAutoTrimEnabled(boolean enabled){autoTrimEnabled=enabled;}
    public boolean isAutoTrimEnabled(){return autoTrimEnabled;}
    private void autoTrimTick(){
        int tempoMaster=masterDeck();DeckState tempoState=state(tempoMaster);if(tempoState.analysis!=null)engine.setRackFxTempo((float)(tempoState.analysis.bpm*engine.playbackRate(tempoMaster)));
        if(lastGrooveMaster<0)lastGrooveMaster=tempoMaster;else if(tempoMaster!=lastGrooveMaster){lastGrooveMaster=tempoMaster;syncPlayingLoopers();}
        if(!autoTrimEnabled)return;int master=tempoMaster,slave=master==0?1:0;if(!engine.isPlaying(master))return;
        AutoTrim.Level ml=levels.get(master),sl=levels.get(slave);if(ml==null||sl==null||!engine.isPlaying(slave))return;
        DeckState ms=state(master),ss=state(slave);Double target=AutoTrim.targetGain(ml,sl,ms.trim);if(target==null)return;
        double current=Math.max(1e-3,ss.trim),delta=20*Math.log10(target/current);if(Math.abs(delta)<.12)return;
        double bounded=Math.max(-.75,Math.min(.75,delta));float next=(float)(current*Math.pow(10,bounded/20));ss.trim=next;engine.setTrim(slave,next);
    }
    public int masterDeck(){boolean a=engine.isPlaying(0),b=engine.isPlaying(1);if(a&&!b)return 0;if(b&&!a)return 1;return engine.crossfader()<=.5f?0:1;}

    public void loadLooper(TrackInfo track, Runnable ready, java.util.function.Consumer<Throwable> error) { loadLooper(0,track,ready,error); }
    public void loadLooper(int slot,TrackInfo track,Runnable ready,java.util.function.Consumer<Throwable> error){
        int target=Math.max(0,Math.min(11,slot));io.execute(()->{try{PcmTrack pcm=PcmDecoder.decode(context,track.uri);AnalysisResult ar=GridRefiner.refine(pcm,BeatAnalyzer.analyze(pcm));engine.loadLooperSlot(target,pcm,ar.bpm);grooveNames[target]=track.title==null||track.title.isBlank()?"LOOP "+(target+1):track.title;groovePrefs.edit().putString("uri."+target,track.uri.toString()).putString("name."+target,grooveNames[target]).apply();main.post(()->{listener.onStateChanged();ready.run();});}catch(Throwable e){main.post(()->error.accept(e));}});
    }
    public void startLooper(){startLooperSlot(0);}
    public void startLooperSlot(int slot){int m=masterDeck();DeckState s=state(m);if(s.analysis==null)return;double bpm=s.analysis.bpm*engine.playbackRate(m);double baseBeat=60.0/Math.max(1,s.analysis.bpm),pos=engine.audiblePosition(m);double sourcePhase=mod(pos-s.analysis.beatgridOffset,baseBeat)/baseBeat;double targetPeriod=60.0/Math.max(1,bpm);double loopOffset=(1-sourcePhase)*targetPeriod;engine.startLooperSlot(Math.max(0,Math.min(11,slot)),bpm,loopOffset);}
    public void toggleLooperSlot(int slot){int target=Math.max(0,Math.min(11,slot));if(engine.isLooperSlotPlaying(target))engine.stopLooperSlot(target);else startLooperSlot(target);listener.onStateChanged();}
    public void syncPlayingLoopers(){for(int i=0;i<12;i++)if(engine.isLooperSlotPlaying(i)){engine.stopLooperSlot(i);startLooperSlot(i);}}
    public void stopLooper(){for(int i=0;i<12;i++)engine.stopLooperSlot(i);}
    public String grooveName(int slot){int s=Math.max(0,Math.min(11,slot));return grooveNames[s]==null?"EMPTY":grooveNames[s];}
    public void clearGrooveSlot(int slot){int s=Math.max(0,Math.min(11,slot));engine.stopLooperSlot(s);engine.clearLooperSlot(s);grooveNames[s]="EMPTY";groovePrefs.edit().remove("uri."+s).remove("name."+s).apply();listener.onStateChanged();}
    public void setGrooveFilter(float value){grooveFilter=Math.max(-1,Math.min(1,value));engine.setLooperTone(grooveFilter,grooveBassDb);groovePrefs.edit().putFloat("filter",grooveFilter).apply();listener.onStateChanged();}
    public void setGrooveBass(float db){grooveBassDb=Math.max(-12,Math.min(6,db));engine.setLooperTone(grooveFilter,grooveBassDb);groovePrefs.edit().putFloat("bass",grooveBassDb).apply();listener.onStateChanged();}
    public void setGrooveVolume(float value){grooveVolume=Math.max(0,Math.min(1,value));engine.setLooperGain(grooveVolume);groovePrefs.edit().putFloat("volume",grooveVolume).apply();listener.onStateChanged();}

    public void triggerSample(int visiblePad){
        int slot=Math.max(0,Math.min(31,visiblePad)); if(samplerMuted)return;
        if(!samplerSync){engine.triggerSample(slot,1);return;}
        int m=masterDeck();DeckState ms=state(m);if(ms.analysis==null||!engine.isPlaying(m)){engine.triggerSample(slot,1);return;}
        double pos=engine.audiblePosition(m),rate=Math.max(.2,engine.playbackRate(m));
        double beat=BeatGridMath.beatAt(ms.analysis,pos),nextBeat=Math.ceil(beat+1e-7);
        if(nextBeat<=beat+1e-6)nextBeat+=1;
        double nextTime=BeatGridMath.timeAt(ms.analysis,nextBeat),delay=Math.max(0,(nextTime-pos)/rate);
        engine.triggerSampleScheduled(slot,1,delay);
    }
    public boolean isSamplerSync(){return samplerSync;}
    public void setSamplerSync(boolean on){samplerSync=on;listener.onStateChanged();}
    public boolean isSamplerMuted(){return samplerMuted;}
    public void setSamplerMuted(boolean on){samplerMuted=on;engine.setPadVolume(on?0:padVolume);listener.onStateChanged();}
    public float padVolume(){return padVolume;}
    public void setPadVolume(float v){padVolume=Math.max(0,Math.min(2f,v));engine.setPadVolume(samplerMuted?0:padVolume);listener.onStateChanged();}
    public String sampleName(int slot){int s=Math.max(0,Math.min(31,slot));return sampleNames[s]==null?"EMPTY":sampleNames[s];}
    public void clearSample(int slot){int target=Math.max(0,Math.min(31,slot));engine.clearSample(target);sampleNames[target]="EMPTY";samplePrefs.edit().remove("uri."+target).remove("name."+target).apply();listener.onStateChanged();}
    public void clearSampleBank(int bank){int b=Math.max(0,Math.min(3,bank));for(int i=0;i<8;i++)clearSample(b*8+i);}
    public void loadSample(int slot,TrackInfo track,Runnable ready,java.util.function.Consumer<Throwable> error){
        int target=Math.max(0,Math.min(31,slot));io.execute(()->{try{PcmTrack pcm=PcmDecoder.decode(context,track.uri);engine.loadSample(target,pcm);sampleNames[target]=track.title==null||track.title.isBlank()?"SAMPLE "+(target+1):track.title;samplePrefs.edit().putString("uri."+target,track.uri.toString()).putString("name."+target,sampleNames[target]).apply();main.post(()->{listener.onStateChanged();ready.run();});}catch(Throwable e){main.post(()->error.accept(e));}});
    }

    private void restoreGrooveLoops(){
        io.execute(()->{
            for(int i=0;i<12;i++){String saved=groovePrefs.getString("uri."+i,null);if(saved==null)continue;try{Uri uri=Uri.parse(saved);PcmTrack pcm=PcmDecoder.decode(context,uri);AnalysisResult ar=GridRefiner.refine(pcm,BeatAnalyzer.analyze(pcm));engine.loadLooperSlot(i,pcm,ar.bpm);}catch(Throwable ignored){}}
            main.post(()->listener.onStateChanged());
        });
    }

    public void hotCue(int deck,int pad){pad=Math.max(0,Math.min(7,pad));double now=engine.position(deck);if(Double.isNaN(hotCues[deck][pad]))hotCues[deck][pad]=now;else engine.seek(deck,hotCues[deck][pad]);listener.onStateChanged();}
    public void clearHotCue(int deck,int pad){if(pad>=0&&pad<8)hotCues[deck][pad]=Double.NaN;}
    public void hotLoop(int deck,int pad){DeckState s=state(deck);if(s.analysis==null)return;double[] beats={1,2,4,8,16,32,.5,.25};double beat=60.0/Math.max(1,s.analysis.bpm),now=engine.position(deck);double start=s.analysis.beatgridOffset+Math.floor((now-s.analysis.beatgridOffset)/beat)*beat;double end=start+beat*beats[Math.max(0,Math.min(7,pad))];s.looping=true;engine.setLoop(deck,true,start,end);listener.onStateChanged();}
    public void stopLoop(int deck){state(deck).looping=false;engine.setLoop(deck,false,0,0);listener.onStateChanged();}
    public void slicer(int deck,int pad){DeckState s=state(deck);if(s.analysis==null)return;double beat=60.0/Math.max(1,s.analysis.bpm),now=engine.position(deck),phrase=s.analysis.beatgridOffset+Math.floor((now-s.analysis.beatgridOffset)/(beat*8))*beat*8;engine.seek(deck,Math.max(0,phrase+beat*Math.max(0,Math.min(7,pad))));}
    public void loopRoll(int deck,int pad,boolean down){if(!down){stopLoop(deck);return;}DeckState s=state(deck);if(s.analysis==null)return;double[] beats={.125,.25,.5,1,2,4,8,16};double beat=60.0/Math.max(1,s.analysis.bpm),now=engine.position(deck),len=beat*beats[Math.max(0,Math.min(7,pad))];engine.setLoop(deck,true,now,now+len);}


    private void preloadFactorySamples(){
        final int[] ids = {R.raw.sample_01,R.raw.sample_02,R.raw.sample_03,R.raw.sample_04,R.raw.sample_05,R.raw.sample_06,R.raw.sample_07,R.raw.sample_08,R.raw.sample_09,R.raw.sample_10,R.raw.sample_11,R.raw.sample_12,R.raw.sample_13,R.raw.sample_14,R.raw.sample_15,R.raw.sample_16,R.raw.sample_17,R.raw.sample_18,R.raw.sample_19,R.raw.sample_20};
        io.execute(()->{
            for(int i=0;i<ids.length;i++){try{Uri uri=Uri.parse("android.resource://"+context.getPackageName()+"/"+ids[i]);PcmTrack pcm=PcmDecoder.decode(context,uri);engine.loadSample(i,pcm);}catch(Throwable ignored){}}
            for(int i=0;i<32;i++){String saved=samplePrefs.getString("uri."+i,null);if(saved==null)continue;try{Uri uri=Uri.parse(saved);PcmTrack pcm=PcmDecoder.decode(context,uri);engine.loadSample(i,pcm);sampleNames[i]=samplePrefs.getString("name."+i,sampleNames[i]);}catch(Throwable ignored){}}
            main.post(()->listener.onStateChanged());
        });
    }

    public synchronized File toggleRecording() {
        if(engine.isRecording()){engine.stopRecording();listener.onStateChanged();return lastRecording;}
        File dir=context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);if(dir==null)dir=context.getFilesDir();if(!dir.exists())dir.mkdirs();
        String stamp=new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date());lastRecording=new File(dir,"DJ-IMAN-Mix-"+stamp+".mp3");
        if(!engine.startRecording(lastRecording.getAbsolutePath())) lastRecording=null;listener.onStateChanged();return lastRecording;
    }
    public File lastRecording(){return lastRecording;}

    private static double mod(double x,double n){return (x%n+n)%n;}
    @Override public void close(){maintenance.shutdownNow();io.shutdownNow();rhythm.close();autoScratch.close();sync.close();engine.close();}
}
