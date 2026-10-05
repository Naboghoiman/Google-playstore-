package com.djiman.bugobi.advance34.automix;

import android.content.Context;
import android.content.SharedPreferences;

import com.djiman.bugobi.advance34.DjController;
import com.djiman.bugobi.advance34.model.DeckState;
import com.djiman.bugobi.advance34.model.TrackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Native equivalent of automix.js, retaining its 3.4 defaults and using the existing sync engine. */
public final class AutomixController implements AutoCloseable {
    public enum StartMode { SECONDS, PERCENT }
    public static final class Settings {
        public double playMinutes=2.5;
        public StartMode startMode=StartMode.SECONDS;
        public double startSeconds=8;
        public double startPercent=5;
        public double transitionSeconds=12;
        public boolean removePlayed=true;
        public boolean samplerEffects=true;
        public int samplerHits=1;
        public int samplerMinPercent=28;
        public int samplerMaxPercent=68;
        public boolean preloadNext=true;
        public boolean syncLockIncoming=false;
        public boolean stopOutgoingAfterTransition=true;
    }

    private final DjController dj;
    private final SharedPreferences prefs;
    private final List<TrackInfo> queue=Collections.synchronizedList(new ArrayList<>());
    private final ScheduledExecutorService clock=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"djiman-automix");t.setDaemon(true);return t;});
    private final Random random=new Random();
    public final Settings settings=new Settings();
    private volatile boolean running=false,transitioning=false;
    private volatile int activeDeck=0;
    private volatile int currentIndex=0;
    private volatile TrackInfo current,next;
    private volatile double playStart=0;

    public AutomixController(Context context,DjController dj){this.dj=dj;prefs=context.getSharedPreferences("djiman.automix",Context.MODE_PRIVATE);loadSettings();clock.scheduleAtFixedRate(this::tickSafe,250,250,TimeUnit.MILLISECONDS);}

    public List<TrackInfo> queueSnapshot(){synchronized(queue){return new ArrayList<>(queue);}}
    public void add(TrackInfo t){if(t!=null&&!queue.contains(t))queue.add(t);}
    public void remove(TrackInfo t){queue.remove(t);}
    public void clear(){queue.clear();}
    public boolean isRunning(){return running;}

    public synchronized void start(){if(running||queue.isEmpty())return;running=true;activeDeck=0;currentIndex=0;current=queue.get(0);next=queue.size()>1?queue.get(1):null;dj.setCrossfader(0);dj.loadTrack(activeDeck,current);}
    public synchronized void stop(){running=false;transitioning=false;dj.engine.pause(0);dj.engine.pause(1);dj.setCrossfader(.5f);}

    private void tickSafe(){try{tick();}catch(Throwable ignored){}}
    private synchronized void tick(){
        if(!running||transitioning||current==null)return;DeckState a=dj.state(activeDeck);
        if(a.loading||a.analysis==null||a.info!=current)return;
        if(!dj.engine.isPlaying(activeDeck)){
            double start=startPoint(a);dj.engine.seek(activeDeck,start);dj.engine.play(activeDeck);playStart=start;
            if(settings.preloadNext&&next!=null)ensureNextLoaded();return;
        }
        if(settings.preloadNext&&next!=null)ensureNextLoaded();
        double now=dj.engine.position(activeDeck),duration=dj.engine.duration(activeDeck),playFor=settings.playMinutes*60.0;
        double transitionAt=Math.min(playStart+playFor,Math.max(playStart,duration-settings.transitionSeconds));
        if(next!=null&&now>=transitionAt)beginTransition();
        else if(next==null&&now>=duration-.2)finishNoNext();
    }
    private double startPoint(DeckState s){double dur=dj.engine.duration(activeDeck);return settings.startMode==StartMode.PERCENT?dur*Math.max(0,Math.min(100,settings.startPercent))/100.0:Math.max(0,settings.startSeconds);}
    private void ensureNextLoaded(){int in=1-activeDeck;DeckState s=dj.state(in);if(s.info!=next&&!s.loading)dj.loadTrack(in,next);}

    private void beginTransition(){
        int out=activeDeck,in=1-activeDeck;DeckState incoming=dj.state(in);if(incoming.loading||incoming.analysis==null||incoming.info!=next){ensureNextLoaded();return;}
        transitioning=true;double start=settings.startMode==StartMode.PERCENT?dj.engine.duration(in)*settings.startPercent/100.0:settings.startSeconds;dj.engine.seek(in,start);dj.sync.syncNow(in);if(settings.syncLockIncoming)dj.sync.setLocked(in,true);dj.engine.play(in);
        long begin=System.nanoTime();int hits=Math.max(0,settings.samplerHits);boolean[] fired={false};
        clock.scheduleAtFixedRate(new Runnable(){@Override public void run(){
            if(!running||!transitioning)return;double t=(System.nanoTime()-begin)/1e9/settings.transitionSeconds;t=Math.max(0,Math.min(1,t));double smooth=t*t*(3-2*t);dj.setCrossfader((float)(in==1?smooth:1-smooth));
            if(settings.samplerEffects&&!fired[0]&&hits>0){double pct=100*t;if(pct>=settings.samplerMinPercent&&pct<=settings.samplerMaxPercent){for(int i=0;i<hits;i++)dj.triggerSample(random.nextInt(8));fired[0]=true;}}
            if(t>=1){finishTransition(out,in);throw new StopTask();}
        }},0,50,TimeUnit.MILLISECONDS);
    }

    private synchronized void finishTransition(int out,int in){if(!transitioning)return;if(settings.stopOutgoingAfterTransition)dj.engine.pause(out);dj.sync.setLocked(out,false);activeDeck=in;
        if(settings.removePlayed){int idx=queue.indexOf(current);if(idx>=0)queue.remove(idx);currentIndex=Math.max(0,Math.min(currentIndex,Math.max(0,queue.size()-1)));}
        else currentIndex++;
        current=next;
        int nextIndex=settings.removePlayed?currentIndex+1:currentIndex+1;
        next=(nextIndex>=0&&nextIndex<queue.size())?queue.get(nextIndex):null;
        playStart=dj.engine.position(in);transitioning=false;if(settings.preloadNext&&next!=null)ensureNextLoaded();}
    private synchronized void finishNoNext(){running=false;}

    public void saveSettings(){prefs.edit().putLong("playMinutes",Double.doubleToRawLongBits(settings.playMinutes)).putString("startMode",settings.startMode.name()).putLong("startSeconds",Double.doubleToRawLongBits(settings.startSeconds)).putLong("startPercent",Double.doubleToRawLongBits(settings.startPercent)).putLong("transitionSeconds",Double.doubleToRawLongBits(settings.transitionSeconds)).putBoolean("removePlayed",settings.removePlayed).putBoolean("samplerEffects",settings.samplerEffects).putInt("samplerHits",settings.samplerHits).putInt("samplerMinPercent",settings.samplerMinPercent).putInt("samplerMaxPercent",settings.samplerMaxPercent).putBoolean("preloadNext",settings.preloadNext).putBoolean("syncLockIncoming",settings.syncLockIncoming).putBoolean("stopOutgoing",settings.stopOutgoingAfterTransition).apply();}
    private void loadSettings(){settings.playMinutes=readDouble("playMinutes",2.5);try{settings.startMode=StartMode.valueOf(prefs.getString("startMode",StartMode.SECONDS.name()));}catch(Exception ignored){}settings.startSeconds=readDouble("startSeconds",8);settings.startPercent=readDouble("startPercent",5);settings.transitionSeconds=readDouble("transitionSeconds",12);settings.removePlayed=prefs.getBoolean("removePlayed",true);settings.samplerEffects=prefs.getBoolean("samplerEffects",true);settings.samplerHits=prefs.getInt("samplerHits",1);settings.samplerMinPercent=prefs.getInt("samplerMinPercent",28);settings.samplerMaxPercent=prefs.getInt("samplerMaxPercent",68);settings.preloadNext=prefs.getBoolean("preloadNext",true);settings.syncLockIncoming=prefs.getBoolean("syncLockIncoming",false);settings.stopOutgoingAfterTransition=prefs.getBoolean("stopOutgoing",true);}
    private double readDouble(String k,double d){return prefs.contains(k)?Double.longBitsToDouble(prefs.getLong(k,Double.doubleToRawLongBits(d))):d;}
    @Override public void close(){clock.shutdownNow();}

    /** Used only to terminate a scheduled transition runnable after completion. */
    private static final class StopTask extends RuntimeException { private StopTask(){super(null,null,false,false);} }
}
