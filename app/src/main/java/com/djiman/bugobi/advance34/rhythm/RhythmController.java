package com.djiman.bugobi.advance34.rhythm;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.djiman.bugobi.advance34.audio.NativeDjEngine;
import com.djiman.bugobi.advance34.model.PcmTrack;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * State/persistence controller for the native 3.4 Rhythm Socket.
 * Pattern timing itself runs inside the AAudio callback; this class never clocks audio.
 */
public final class RhythmController implements AutoCloseable {
    private static final String PREFS = "djiman.native.rhythm";
    private static final int[][] INITIAL_STEPS = {
            {0,4,8,12,14}, {3,6,11}, {1,2,3,5,6,9,10,11,14}, {2}, {}, {}
    };

    private final NativeDjEngine engine;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService synth = Executors.newSingleThreadExecutor(r -> {
        Thread t=new Thread(r,"djiman-rhythm-synth");t.setDaemon(true);return t;
    });
    private final Runnable changed;
    private final int[] revisions = new int[RhythmKit.INTERNAL_ROWS];

    private final int[] masks = new int[RhythmKit.INTERNAL_ROWS];
    private int enabledMask=(1<<RhythmKit.INTERNAL_ROWS)-1;
    private final LinkedHashMap<String,String> sounds = new LinkedHashMap<>();
    private volatile String kitPreset="default";
    private volatile boolean loading=true;
    private volatile int keySemitones=0;

    public RhythmController(Context context,NativeDjEngine engine,Runnable changed){
        this.engine=engine;
        this.changed=changed==null?()->{}:changed;
        this.prefs=context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        restore();
        pushPattern();
        engine.setRhythmKey(keySemitones);
        loadAllSounds();
    }

    private void restore(){
        resetStateOnly();
        String packed=prefs.getString("pattern",null);
        if(packed!=null){
            String[] parts=packed.split(",");
            for(int i=0;i<Math.min(parts.length,masks.length);i++){
                try{masks[i]=Integer.parseInt(parts[i],16)&0xFFFF;}catch(Exception ignored){}
            }
        }
        enabledMask=prefs.getInt("enabledMask",enabledMask)&((1<<RhythmKit.INTERNAL_ROWS)-1);
        String soundPacked=prefs.getString("sounds",null);
        if(soundPacked!=null){
            for(String pair:soundPacked.split(";")){
                int eq=pair.indexOf('=');
                if(eq<=0)continue;
                String family=pair.substring(0,eq),sound=pair.substring(eq+1);
                RhythmKit.Family f=RhythmKit.family(family);
                if(f!=null)sounds.put(family,f.sound(sound).id);
            }
        }
        kitPreset=prefs.getString("preset",soundPacked==null?"default":"custom");
        keySemitones=Math.max(-12,Math.min(12,prefs.getInt("key",0)));
    }

    private void resetStateOnly(){
        for(int r=0;r<masks.length;r++)masks[r]=0;
        for(int r=0;r<INITIAL_STEPS.length;r++)for(int step:INITIAL_STEPS[r])masks[r]|=1<<step;
        enabledMask=(1<<RhythmKit.INTERNAL_ROWS)-1;
        sounds.clear();sounds.putAll(RhythmKit.defaultSounds());
        kitPreset="default";keySemitones=0;
    }

    private void loadAllSounds(){
        loading=true;notifyChanged();
        synth.execute(()->{
            for(RhythmKit.Family f:RhythmKit.FAMILIES){
                try{
                    PcmTrack pcm=RhythmKit.synthesize(f.id,soundId(f.id));
                    engine.loadRhythmRow(f.row,pcm);
                }catch(Throwable ignored){}
            }
            loading=false;notifyChanged();
        });
    }

    private void loadFamilyAsync(RhythmKit.Family f){
        final int row=f.row;
        final int rev=++revisions[row];
        final String sound=soundId(f.id);
        loading=true;notifyChanged();
        synth.execute(()->{
            try{
                PcmTrack pcm=RhythmKit.synthesize(f.id,sound);
                if(revisions[row]==rev)engine.loadRhythmRow(row,pcm);
            }finally{
                loading=false;notifyChanged();
            }
        });
    }

    public boolean isLoading(){return loading;}
    public boolean isActive(){return engine.isRhythmActive();}
    public void toggleActive(){setActive(!isActive());}
    public void setActive(boolean active){engine.setRhythmActive(active);notifyChanged();}
    public int currentStep(){return engine.rhythmCurrentStep();}

    public boolean step(int row,int step){
        if(row<0||row>=masks.length||step<0||step>=16)return false;
        return (masks[row]&(1<<step))!=0;
    }
    public void toggleStep(int row,int step){
        if(row<0||row>=masks.length||step<0||step>=16)return;
        masks[row]^=1<<step;pushPattern();notifyChanged();
    }
    public boolean rowEnabled(int row){return row>=0&&row<masks.length&&(enabledMask&(1<<row))!=0;}
    public void toggleRow(int row){
        if(row<0||row>=masks.length)return;
        enabledMask^=1<<row;pushPattern();notifyChanged();
    }

    public int keySemitones(){return keySemitones;}
    public void setKeySemitones(int value){
        keySemitones=Math.max(-12,Math.min(12,value));engine.setRhythmKey(keySemitones);notifyChanged();
    }
    public void shiftPattern(int steps){engine.shiftRhythm(steps);notifyChanged();}

    public String soundId(String familyId){
        RhythmKit.Family f=RhythmKit.family(familyId);if(f==null)return "";
        String id=sounds.get(familyId);return f.sound(id).id;
    }
    public String soundName(String familyId){
        RhythmKit.Family f=RhythmKit.family(familyId);return f==null?"":f.sound(soundId(familyId)).name;
    }
    public String kitPreset(){return kitPreset;}
    public String kitPresetName(){
        if("default".equals(kitPreset))return "DJ DEFAULTS";
        RhythmKit.Preset p=RhythmKit.preset(kitPreset);return p==null?"CUSTOM SOUNDS":p.name;
    }

    public void chooseSound(String familyId,String soundId){
        RhythmKit.Family f=RhythmKit.family(familyId);
        if(f==null)return;
        String valid=f.sound(soundId).id;
        if(valid.equals(this.sounds.get(familyId)))return;
        this.sounds.put(familyId,valid);kitPreset="custom";loadFamilyAsync(f);notifyChanged();
    }

    public void choosePreset(String id){
        RhythmKit.Preset p=RhythmKit.preset(id);
        if(p==null&&!"default".equals(id))return;
        Map<String,String> next=RhythmKit.defaultSounds();
        if(p!=null)next.putAll(p.sounds);
        for(RhythmKit.Family f:RhythmKit.FAMILIES){
            String valid=f.sound(next.get(f.id)).id;
            if(!valid.equals(sounds.get(f.id))){
                sounds.put(f.id,valid);loadFamilyAsync(f);
            }
        }
        kitPreset=id;notifyChanged();
    }

    public void clearPattern(){
        for(int i=0;i<masks.length;i++)masks[i]=0;
        pushPattern();notifyChanged();
    }

    public void reset(){
        resetStateOnly();pushPattern();engine.setRhythmKey(0);
        for(RhythmKit.Family f:RhythmKit.FAMILIES)loadFamilyAsync(f);
        notifyChanged();
    }

    public void save(){
        StringBuilder pattern=new StringBuilder();
        for(int i=0;i<masks.length;i++){if(i>0)pattern.append(',');pattern.append(Integer.toHexString(masks[i]&0xFFFF));}
        StringBuilder soundText=new StringBuilder();
        for(Map.Entry<String,String> e:sounds.entrySet()){
            if(soundText.length()>0)soundText.append(';');
            soundText.append(e.getKey()).append('=').append(e.getValue());
        }
        prefs.edit().putString("pattern",pattern.toString())
                .putInt("enabledMask",enabledMask)
                .putString("sounds",soundText.toString())
                .putString("preset",kitPreset)
                .putInt("key",keySemitones)
                .apply();
        notifyChanged();
    }

    public String statusText(){
        if(loading)return "Loading sounds…";
        if(!isActive())return "Ready";
        int step=currentStep();
        return step<0?"Queued · next bar":String.format(Locale.US,"In sync · step %d",step+1);
    }

    private void pushPattern(){engine.setRhythmPattern(masks,enabledMask,1.0f);}
    private void notifyChanged(){main.post(changed);}

    @Override public void close(){synth.shutdownNow();}
}
