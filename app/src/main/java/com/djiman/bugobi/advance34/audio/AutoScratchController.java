package com.djiman.bugobi.advance34.audio;

import com.djiman.bugobi.advance34.model.DeckState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Native hold/release DNA scratch controller using the pattern set shipped in deck-controls.js. */
public final class AutoScratchController implements AutoCloseable {
    private static final Map<String,String> PATTERNS=Map.of(
            "BABY","ABBA.BAAB.","CHIRP","ABaB.BaBA.","TRANSFORMER","AaAa.BbBb.","TEAR","ACDA.DCBA.",
            "FLARE1","ABaB.BAbA.","FLARE2","ABaBaB.BAbAbA.","ORBIT","ABaBaBA.BAbAbAB.","CRAB","AaAaAaBb.BbBbBbAa.");
    private final NativeDjEngine engine;
    private final DeckState a,b;
    private final ScheduledExecutorService clock=Executors.newScheduledThreadPool(2,r->{Thread t=new Thread(r,"djiman-auto-scratch");t.setDaemon(true);return t;});
    private final State[] state={new State(),new State()};
    private volatile String pattern="BABY";
    private volatile double depth=1.0;

    public AutoScratchController(NativeDjEngine e,DeckState a,DeckState b){engine=e;this.a=a;this.b=b;clock.scheduleAtFixedRate(()->tick(0),0,10,TimeUnit.MILLISECONDS);clock.scheduleAtFixedRate(()->tick(1),0,10,TimeUnit.MILLISECONDS);}
    public void setPattern(String name){String n=name==null?"BABY":name.toUpperCase();if(PATTERNS.containsKey(n))pattern=n;}
    public void setDepth(double value){depth=Math.max(.25,Math.min(2.5,value));}
    public synchronized void begin(int deck){deck=deck==1?1:0;State s=state[deck];s.active=true;s.anchor=engine.position(deck);s.offset=0;s.started=System.nanoTime();s.last=s.started;engine.beginScratch(deck);}
    public synchronized void end(int deck){deck=deck==1?1:0;State s=state[deck];if(!s.active)return;s.active=false;engine.endScratch(deck);}
    public boolean active(int deck){return state[deck==1?1:0].active;}
    private void tick(int deck){State s=state[deck];if(!s.active)return;DeckState ds=deck==0?a:b;double bpm=(ds.analysis==null?120:ds.analysis.bpm)*engine.playbackRate(deck);double beat=60.0/Math.max(30,bpm),stepDur=beat/4.0;String pat=PATTERNS.getOrDefault(pattern,PATTERNS.get("BABY"));long now=System.nanoTime();double elapsed=(now-s.started)/1e9;int index=(int)Math.floor(elapsed/stepDur)%pat.length();char ch=pat.charAt(index);double velocity=velocity(ch)*depth;double dt=Math.min(.03,Math.max(0,(now-s.last)/1e9));s.last=now;s.offset+=velocity*dt;s.offset=Math.max(-5,Math.min(5,s.offset));engine.moveScratch(deck,Math.max(0,s.anchor+s.offset),velocity);}
    private static double velocity(char c){return switch(c){case 'A'->1.8;case 'a'->1.0;case 'B'->-1.8;case 'b'->-1.0;case 'C'->.75;case 'D'->-.75;default->0;};}
    @Override public void close(){end(0);end(1);clock.shutdownNow();}
    private static final class State{volatile boolean active;double anchor,offset;long started,last;}
}
