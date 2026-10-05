package com.djiman.bugobi.advance34.rhythm;

import com.djiman.bugobi.advance34.model.PcmTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Pure-Java offline drum synthesis port of the packaged 3.4 live-drum kit.
 * It runs only when a sound is selected; the resulting PCM is rendered by the
 * native AAudio Rhythm Socket.
 */
public final class RhythmKit {
    public static final int INTERNAL_ROWS = 18;
    public static final int STEPS = 16;
    public static final int SYNTH_SAMPLE_RATE = 24000;

    public static final class Params {
        String kind = "fx";
        double duration, frequency, drop, decay, click, harmonic, level;
        double noise, body, brightness, rim, spacing, spread, metal, grain, ratio, slap;
        boolean reverse, sweep;
        Params copy() {
            Params p = new Params();
            p.kind=kind;p.duration=duration;p.frequency=frequency;p.drop=drop;p.decay=decay;p.click=click;
            p.harmonic=harmonic;p.level=level;p.noise=noise;p.body=body;p.brightness=brightness;p.rim=rim;
            p.spacing=spacing;p.spread=spread;p.metal=metal;p.grain=grain;p.ratio=ratio;p.slap=slap;
            p.reverse=reverse;p.sweep=sweep; return p;
        }
    }

    public static final class Sound {
        public final String id, name;
        private final Consumer<Params> patch;
        Sound(String id,String name,Consumer<Params> patch){this.id=id;this.name=name;this.patch=patch;}
        Params apply(Params base){Params p=base.copy();if(patch!=null)patch.accept(p);return p;}
    }

    public static final class Family {
        public final String id, name, defaultId;
        public final int row;
        final Params base;
        public final List<Sound> sounds;
        Family(String id,int row,String name,String defaultId,Params base,List<Sound> sounds){
            this.id=id;this.row=row;this.name=name;this.defaultId=defaultId;this.base=base;
            this.sounds=Collections.unmodifiableList(sounds);
        }
        public Sound sound(String id){
            for(Sound s:sounds)if(s.id.equals(id))return s;
            for(Sound s:sounds)if(s.id.equals(defaultId))return s;
            return sounds.get(0);
        }
    }

    public static final class Preset {
        public final String id,name;
        public final Map<String,String> sounds;
        Preset(String id,String name,Map<String,String> sounds){this.id=id;this.name=name;this.sounds=Collections.unmodifiableMap(sounds);}
    }

    private static Params p(String kind,double duration,double frequency,double drop,double decay,double click,double harmonic,double level){
        Params p=new Params();p.kind=kind;p.duration=duration;p.frequency=frequency;p.drop=drop;p.decay=decay;p.click=click;p.harmonic=harmonic;p.level=level;return p;
    }
    private static Sound v(String id,String name){return new Sound(id,name,null);}
    private static Sound v(String id,String name,Consumer<Params> patch){return new Sound(id,name,patch);}
    private static Family f(String id,int row,String name,String def,Params base,Sound... sounds){
        List<Sound> a=new ArrayList<>();Collections.addAll(a,sounds);return new Family(id,row,name,def,base,a);
    }
    private static Map<String,String> map(String... kv){
        LinkedHashMap<String,String> m=new LinkedHashMap<>();
        for(int i=0;i+1<kv.length;i+=2)m.put(kv[i],kv[i+1]);
        return m;
    }

    public static final List<Family> FAMILIES;
    public static final List<Preset> PRESETS;
    static {
        List<Family> fs=new ArrayList<>();

        Params kick=p("kick",.22,56,85,.052,.065,.10,.44);
        fs.add(f("kick",0,"KICK","reggaeton",kick,
                v("soft","Soft DJ Kick",x->{x.duration=.18;x.drop=52;x.click=.022;x.level=.32;}),
                v("punchy","Punchy Kick",x->{x.frequency=62;x.drop=105;x.click=.09;x.decay=.045;}),
                v("deep","Deep Kick",x->{x.duration=.29;x.frequency=48;x.drop=67;x.decay=.07;x.level=.42;}),
                v("reggaeton","Short Punchy Reggaeton Kick",x->{x.duration=.20;x.frequency=57;x.drop=92;x.decay=.047;x.click=.055;}),
                v("dancehall","Dancehall Kick",x->{x.duration=.24;x.frequency=53;x.drop=69;x.decay=.06;x.click=.04;x.harmonic=.14;}),
                v("afro","Afro Kick",x->{x.duration=.21;x.frequency=64;x.drop=64;x.decay=.051;x.click=.035;x.harmonic=.17;}),
                v("house","House Kick",x->{x.duration=.25;x.frequency=55;x.drop=118;x.decay=.061;x.click=.075;}),
                v("short-sub","Short Sub Kick",x->{x.duration=.18;x.frequency=46;x.drop=29;x.decay=.045;x.click=.013;x.harmonic=.04;x.level=.34;})));

        Params sn=p("snare",.18,190,0,.034,0,0,.35);sn.noise=.67;sn.body=.33;sn.brightness=5700;
        fs.add(f("snare",1,"SNARE","tight",sn,
                v("tight","Tight Dry Snare"),
                v("bright","Bright Snare",x->{x.brightness=7100;x.noise=.8;x.body=.22;}),
                v("dembow","Dembow Snare",x->{x.frequency=220;x.body=.43;x.noise=.52;x.decay=.029;}),
                v("dancehall","Dancehall Snare",x->{x.frequency=170;x.body=.46;x.brightness=4400;x.decay=.04;}),
                v("rim","Rim Snare",x->{x.frequency=275;x.body=.53;x.noise=.32;x.rim=.22;x.duration=.14;}),
                v("electronic","Electronic Snare",x->{x.frequency=245;x.body=.5;x.noise=.52;x.decay=.026;}),
                v("soft","Soft Snare",x->{x.brightness=3500;x.noise=.48;x.body=.3;x.level=.26;})));

        Params clap=p("clap",.15,0,0,.024,0,0,.30);clap.brightness=5800;clap.spacing=.006;clap.spread=0;
        fs.add(f("clap",6,"CLAP","bright",clap,
                v("short","Short Clap",x->{x.duration=.12;x.decay=.017;}),
                v("bright","Short Bright Clap"),
                v("wide","Wide Clap",x->{x.spread=.26;x.decay=.029;x.duration=.18;}),
                v("dembow","Dembow Clap",x->{x.brightness=6200;x.spacing=.004;x.decay=.020;}),
                v("dancehall","Dancehall Clap",x->{x.brightness=4200;x.spacing=.008;x.decay=.026;}),
                v("soft","Soft Clap",x->{x.brightness=3200;x.level=.23;}),
                v("layered","Layered Clap",x->{x.spacing=.009;x.decay=.031;x.duration=.20;x.spread=.10;})));

        Params ch=p("hat",.065,0,0,.014,0,0,.14);ch.brightness=7800;ch.metal=.16;
        fs.add(f("closed-hat",2,"CLOSED HI-HAT","tight",ch,
                v("tight","Tight Short Closed Hat"),
                v("soft","Soft Closed Hat",x->{x.brightness=4600;x.metal=.07;x.level=.105;}),
                v("bright","Bright Hat",x->{x.brightness=9200;x.level=.145;}),
                v("metallic","Metallic Hat",x->{x.metal=.39;x.duration=.075;x.decay=.017;}),
                v("house","House Hat",x->{x.decay=.019;x.duration=.085;x.metal=.22;}),
                v("dancehall","Dancehall Hat",x->{x.brightness=5800;x.decay=.012;x.duration=.058;}),
                v("reggaeton","Reggaeton Hat",x->{x.brightness=6800;x.metal=.12;x.decay=.011;x.duration=.055;})));

        Params sh=p("shaker",.13,0,0,.029,0,0,.14);sh.brightness=6800;sh.grain=77;
        fs.add(f("shaker",10,"SHAKER","latin",sh,
                v("latin","Fine Latin Shaker"),
                v("soft","Soft Shaker",x->{x.brightness=4200;x.level=.105;}),
                v("rough","Rough Shaker",x->{x.grain=47;x.brightness=5600;x.decay=.035;x.duration=.16;}),
                v("maraca","Maraca",x->{x.grain=33;x.brightness=3700;x.decay=.037;x.duration=.17;}),
                v("afro","Afro Shaker",x->{x.grain=59;x.brightness=5100;}),
                v("dancehall","Dancehall Shaker",x->{x.grain=91;x.decay=.024;x.duration=.11;}),
                v("reggaeton","Reggaeton Shaker",x->{x.grain=110;x.decay=.022;x.duration=.105;})));

        Params rim=p("wood",.10,890,0,.019,0,0,.27);rim.ratio=1.74;rim.noise=.025;
        fs.add(f("rimshot",3,"RIMSHOT","dry",rim,
                v("dry","Dry Rimshot"),
                v("bright","Bright Rim",x->{x.frequency=1190;x.decay=.015;}),
                v("deep","Deep Rim",x->{x.frequency=610;x.ratio=1.53;x.duration=.13;x.decay=.025;}),
                v("sidestick","Sidestick",x->{x.frequency=750;x.ratio=2.32;x.noise=.013;x.level=.23;}),
                v("reggaeton","Reggaeton Rim",x->{x.frequency=980;x.ratio=1.91;x.decay=.017;}),
                v("dancehall","Dancehall Rim",x->{x.frequency=670;x.ratio=1.66;x.decay=.021;})));

        Params clave=p("wood",.09,2350,0,.016,0,0,.23);clave.ratio=1.53;clave.noise=.004;
        fs.add(f("clave",14,"CLAVE","wooden",clave,
                v("wooden","Short Wooden Clave"),
                v("hard","Hard Clave",x->{x.frequency=2680;x.noise=.014;x.decay=.014;}),
                v("soft","Soft Clave",x->{x.frequency=1950;x.level=.17;}),
                v("latin","Latin Clave",x->{x.frequency=2210;x.ratio=1.71;}),
                v("bright","Bright Clave",x->{x.frequency=2890;x.ratio=1.47;})));

        Params conga=p("conga",.19,235,0,.04,0,0,.32);conga.noise=.075;conga.slap=.08;
        fs.add(f("conga",4,"CONGA","muted",conga,
                v("muted","Muted / Slap Conga",x->{x.duration=.14;x.decay=.025;x.slap=.12;}),
                v("slap","Slap Conga",x->{x.slap=.36;x.noise=.16;x.decay=.028;x.duration=.15;}),
                v("high","High Conga",x->{x.frequency=305;x.decay=.033;x.duration=.17;}),
                v("low","Low Conga",x->{x.frequency=175;x.decay=.048;x.duration=.23;}),
                v("dry","Dry Conga",x->{x.noise=.02;x.slap=.015;x.decay=.03;x.duration=.15;}),
                v("afro","Afro Conga",x->{x.frequency=210;x.slap=.17;x.decay=.042;})));

        Params oh=p("hat",.19,0,0,.047,0,0,.15);oh.brightness=7400;oh.metal=.23;
        fs.add(f("open-hat",9,"OPEN HI-HAT","short",oh,
                v("short","Short Open Hat"),
                v("bright","Bright Open Hat",x->x.brightness=9300),
                v("soft","Soft Open Hat",x->{x.brightness=4300;x.metal=.10;x.level=.11;}),
                v("metallic","Metallic Open Hat",x->{x.metal=.43;x.decay=.055;x.duration=.23;}),
                v("house","House Open Hat",x->{x.brightness=6800;x.decay=.061;x.duration=.26;})));

        Params ht=p("tom",.20,165,77,.042,0,0,.33);
        fs.add(f("high-tom",8,"HIGH TOM","short",ht,
                v("short","Short High Tom"),
                v("tight","Tight High Tom",x->{x.frequency=190;x.decay=.032;x.duration=.16;}),
                v("electronic","Electronic High Tom",x->{x.frequency=208;x.drop=115;x.decay=.038;}),
                v("afro","Afro High Tom",x->{x.frequency=150;x.drop=44;x.decay=.047;})));

        Params lt=p("tom",.25,101,62,.055,0,0,.34);
        fs.add(f("low-tom",7,"LOW TOM","short",lt,
                v("short","Short Low Tom"),
                v("deep","Deep Low Tom",x->{x.frequency=82;x.decay=.06;x.duration=.28;}),
                v("electronic","Electronic Low Tom",x->{x.frequency=115;x.drop=125;x.decay=.044;x.duration=.22;}),
                v("afro","Afro Low Tom",x->{x.frequency=96;x.drop=36;x.decay=.053;})));

        Params guiro=p("guiro",.19,1730,0,.07,0,0,.18);guiro.grain=71;guiro.brightness=5200;
        fs.add(f("guiro",12,"GUIRO","short",guiro,
                v("short","Short Dry Guiro"),
                v("long","Long Guiro",x->{x.duration=.38;x.decay=.15;x.grain=61;}),
                v("bright","Bright Guiro",x->{x.frequency=2240;x.brightness=7300;x.grain=87;}),
                v("soft","Soft Guiro",x->{x.brightness=3300;x.grain=55;x.level=.13;})));

        Params crash=p("crash",.43,0,0,.10,0,0,.21);crash.brightness=6500;crash.metal=.28;
        fs.add(f("crash",13,"CRASH CYMBAL","short",crash,
                v("short","Short Controlled Crash"),
                v("bright","Bright Crash",x->{x.brightness=9000;x.decay=.12;x.duration=.50;}),
                v("dark","Dark Crash",x->{x.brightness=3800;x.metal=.32;x.decay=.13;x.duration=.54;}),
                v("soft","Soft Crash",x->{x.brightness=4300;x.level=.15;x.decay=.09;}),
                v("transition","Transition Crash",x->{x.decay=.145;x.duration=.60;x.level=.20;})));

        Params wood=p("wood",.12,780,0,.026,0,0,.24);wood.ratio=1.65;wood.noise=.011;
        fs.add(f("woodblock",15,"WOODBLOCK","dry",wood,
                v("dry","Dry Woodblock"),
                v("bright","Bright Woodblock",x->{x.frequency=1080;x.ratio=1.57;}),
                v("deep","Deep Woodblock",x->{x.frequency=530;x.ratio=1.78;x.duration=.15;x.decay=.033;}),
                v("short","Short Woodblock",x->{x.duration=.075;x.decay=.013;})));

        Params sub=p("kick",.24,46,24,.055,.007,.025,.32);
        fs.add(f("sub",16,"808 / SUB-BASS KICK","clean",sub,
                v("clean","Short Clean Sub"),
                v("soft","Soft Sub Kick",x->{x.frequency=48;x.drop=16;x.level=.26;x.duration=.21;}),
                v("punchy","Punchy 808",x->{x.frequency=51;x.drop=57;x.click=.027;x.duration=.23;}),
                v("deep","Deep 808",x->{x.frequency=41;x.drop=19;x.decay=.072;x.duration=.30;x.level=.31;}),
                v("reggaeton","Short Reggaeton Sub",x->{x.frequency=52;x.drop=37;x.decay=.046;x.duration=.20;})));

        Params fx=p("fx",.26,490,0,.046,0,0,.25);fx.brightness=4700;fx.noise=.20;
        fs.add(f("fx",17,"FX PERCUSSION","transition",fx,
                v("impact","Short Impact",x->{x.frequency=360;x.noise=.13;x.decay=.035;x.duration=.20;}),
                v("reverse","Reverse Hit",x->{x.reverse=true;x.duration=.20;x.level=.20;}),
                v("noise","Noise Hit",x->{x.noise=.90;x.decay=.03;x.duration=.16;x.level=.18;}),
                v("percussion","Percussion FX",x->{x.frequency=770;x.noise=.07;x.decay=.036;x.duration=.19;}),
                v("transition","Short Transition Impact"),
                v("sweep","Short Sweep",x->{x.sweep=true;x.noise=.72;x.duration=.29;x.decay=.072;x.level=.18;})));

        FAMILIES=Collections.unmodifiableList(fs);

        List<Preset> ps=new ArrayList<>();
        ps.add(new Preset("reggaeton","REGGAETON / DEMBOW",map("kick","reggaeton","snare","dembow","clap","short","closed-hat","tight","shaker","latin","rimshot","dry","clave","wooden","conga","muted")));
        ps.add(new Preset("dancehall","DANCEHALL",map("kick","dancehall","snare","tight","clap","short","closed-hat","dancehall","shaker","dancehall","rimshot","dancehall","woodblock","dry","conga","muted")));
        ps.add(new Preset("afrobeat","AFROBEAT",map("kick","afro","snare","tight","clap","soft","closed-hat","soft","shaker","afro","rimshot","dry","conga","afro","woodblock","dry")));
        ps.add(new Preset("house","HOUSE",map("kick","house","clap","bright","closed-hat","tight","open-hat","short","shaker","soft","crash","short")));
        ps.add(new Preset("latin","LATIN",map("kick","punchy","snare","tight","clap","short","shaker","latin","clave","latin","conga","muted","guiro","short","woodblock","dry")));
        PRESETS=Collections.unmodifiableList(ps);
    }

    private RhythmKit(){}

    public static Family family(String id){for(Family f:FAMILIES)if(f.id.equals(id))return f;return null;}
    public static Family familyByVisibleIndex(int i){return i>=0&&i<FAMILIES.size()?FAMILIES.get(i):null;}
    public static Preset preset(String id){for(Preset p:PRESETS)if(p.id.equals(id))return p;return null;}

    public static Map<String,String> defaultSounds(){
        LinkedHashMap<String,String> m=new LinkedHashMap<>();
        for(Family f:FAMILIES)m.put(f.id,f.defaultId);
        return m;
    }

    public static PcmTrack synthesize(String familyId,String soundId){
        Family fam=family(familyId);if(fam==null)throw new IllegalArgumentException("Unknown drum family "+familyId);
        Sound sound=fam.sound(soundId);
        Params p=sound.apply(fam.base);
        int sr=SYNTH_SAMPLE_RATE;
        int length=Math.max(2,(int)Math.ceil(sr*p.duration));
        float[] stereo=new float[length*2];

        int seed=0x811c9dc5;
        String key=fam.id+"/"+sound.id;
        for(int i=0;i<key.length();i++)seed=(seed^key.charAt(i))*16777619;

        double tau=2*Math.PI;
        double cut=Math.min(p.brightness>0?p.brightness:6500,sr*.42);
        double lpAlpha=1-Math.exp(-tau*cut/sr);
        double hpAlpha=1-Math.exp(-tau*900/sr);
        double dcAlpha=1-Math.exp(-tau*18/sr);
        double phase=0,low=0,bright=0,dcL=0,dcR=0;
        double peak=0;

        for(int i=0;i<length;i++){
            double t=i/(double)sr;
            seed=seed*1664525+1013904223;double n=Integer.toUnsignedLong(seed)/2147483648.0-1;
            seed=seed*1664525+1013904223;double nr=Integer.toUnsignedLong(seed)/2147483648.0-1;
            double env=Math.exp(-t/Math.max(1e-6,p.decay));
            low+=hpAlpha*(n-low);
            bright+=lpAlpha*(n-low-bright);
            double band=bright;
            double val=0,side=0;

            switch(p.kind){
                case "kick" -> {
                    phase+=tau*(p.frequency+p.drop*Math.exp(-t*75))/sr;
                    val=(Math.sin(phase)+p.harmonic*Math.sin(2*phase))*env+p.click*band*Math.exp(-t*300);
                }
                case "snare" -> {
                    val=p.noise*band*env+p.body*(Math.sin(tau*p.frequency*t)+.32*Math.sin(tau*p.frequency*1.71*t))*Math.exp(-t/(Math.max(1e-6,p.decay*.75)));
                    val+=p.rim*Math.sin(tau*960*t)*Math.exp(-t*100);
                }
                case "clap" -> {
                    double burst=0;
                    double[] ats={0,p.spacing,p.spacing*2};
                    for(double at:ats)if(t>=at)burst+=Math.exp(-(t-at)/.0045);
                    double tail=t>=p.spacing*2?.45*Math.exp(-(t-p.spacing*2)/Math.max(1e-6,p.decay)):0;
                    val=band*(burst+tail);side=p.spread*nr*(burst+tail);
                }
                case "hat","crash" -> {
                    double metal=0;double[] freqs={2131,3253,4817,6241,8173};
                    for(double f:freqs)metal+=Math.sin(tau*f*t);
                    val=(.7*band+p.metal*metal*.2)*env;
                }
                case "shaker" -> {
                    double grains=.5+.5*Math.pow(Math.sin(tau*p.grain*t),2);
                    val=band*grains*env*Math.min(1,t/.003);
                }
                case "wood" -> val=(Math.sin(tau*p.frequency*t)+.36*Math.sin(tau*p.frequency*p.ratio*t))*env+p.noise*n*Math.exp(-t*250);
                case "conga" -> {
                    phase+=tau*(p.frequency+32*Math.exp(-t*95))/sr;
                    val=(Math.sin(phase)+.32*Math.sin(phase*1.48)+.13*Math.sin(phase*2.08))*env;
                    val+=(p.noise*band+p.slap*Math.sin(tau*1170*t))*Math.exp(-t*110);
                }
                case "tom" -> {
                    phase+=tau*(p.frequency+p.drop*Math.exp(-t*56))/sr;
                    val=(Math.sin(phase)+.12*Math.sin(phase*1.59))*env+.026*band*Math.exp(-t*200);
                }
                case "guiro" -> {
                    double scrape=Math.pow(.5+.5*Math.cos(tau*(p.grain*t+30*t*t)),7);
                    val=(.7*band+.12*Math.sin(tau*p.frequency*t))*scrape*env;
                }
                default -> {
                    double u=p.reverse?p.duration-t:t;
                    double shape=p.reverse?Math.pow(t/p.duration,2):Math.exp(-t/Math.max(1e-6,p.decay));
                    double freq=p.sweep?p.frequency+1800*(1-t/p.duration):p.frequency;
                    phase+=tau*freq/sr;
                    val=((1-p.noise)*(.65*Math.sin(phase)+.2*Math.sin(phase*1.61))+p.noise*band)*shape;
                    if(p.reverse)val*=Math.min(1,u/.012);
                }
            }

            dcL+=dcAlpha*(val-dcL);
            dcR+=dcAlpha*(val+side-dcR);
            double attack=Math.min(1,t/(p.kind.equals("kick")?.0015:.0006));
            double release=Math.min(1,(length-1-i)/Math.max(1.0,sr*.01));
            float l=(float)((val-dcL)*attack*release);
            float r=(float)((val+side-dcR)*attack*release);
            stereo[i*2]=l;stereo[i*2+1]=r;
            peak=Math.max(peak,Math.max(Math.abs(l),Math.abs(r)));
        }

        double gain=p.level/Math.max(peak,1e-6);
        for(int i=0;i<stereo.length;i++)stereo[i]*=(float)gain;
        return new PcmTrack(stereo,sr);
    }
}
