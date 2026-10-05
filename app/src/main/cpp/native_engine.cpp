#include <jni.h>
#include <aaudio/AAudio.h>
#include <android/log.h>
#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <condition_variable>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

extern "C" {
#include "lame/include/lame.h"
}

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "DJIMAN-NATIVE", __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "DJIMAN-NATIVE", __VA_ARGS__)

namespace {
constexpr double PI = 3.14159265358979323846;
constexpr int GRAIN = 1024;
constexpr int HOP = 256;
constexpr int OVERLAP = GRAIN - HOP;
constexpr int SEARCH = 96;
constexpr int CORR = 192;
constexpr int MAX_SAMPLE_VOICES = 24;
constexpr int SAMPLE_SLOTS = 32;
constexpr int RHYTHM_ROWS = 18;
constexpr int RHYTHM_STEPS = 16;
constexpr int MAX_RHYTHM_VOICES = 32;

inline float clampf(float x, float lo, float hi) { return std::max(lo, std::min(hi, x)); }
inline double clampd(double x, double lo, double hi) { return std::max(lo, std::min(hi, x)); }
inline float dbGain(float db) { return std::pow(10.0f, db / 20.0f); }

struct AudioBuffer {
    std::vector<float> stereo;
    int sampleRate = 44100;
    int64_t frames() const { return static_cast<int64_t>(stereo.size() / 2); }
};

struct Biquad {
    double b0=1,b1=0,b2=0,a1=0,a2=0,z1L=0,z2L=0,z1R=0,z2R=0;
    void reset(){z1L=z2L=z1R=z2R=0;}
    void set(double nb0,double nb1,double nb2,double na0,double na1,double na2){b0=nb0/na0;b1=nb1/na0;b2=nb2/na0;a1=na1/na0;a2=na2/na0;}
    void peak(double sr,double f,double q,double db){
        double A=std::pow(10.0,db/40.0),w=2*PI*f/sr,alpha=std::sin(w)/(2*q),c=std::cos(w);
        set(1+alpha*A,-2*c,1-alpha*A,1+alpha/A,-2*c,1-alpha/A);
    }
    void lowShelf(double sr,double f,double db){
        double A=std::pow(10.0,db/40.0),w=2*PI*f/sr,c=std::cos(w),s=std::sin(w),beta=std::sqrt(A+A); 
        set(A*((A+1)-(A-1)*c+beta*s),2*A*((A-1)-(A+1)*c),A*((A+1)-(A-1)*c-beta*s),
            (A+1)+(A-1)*c+beta*s,-2*((A-1)+(A+1)*c),(A+1)+(A-1)*c-beta*s);
    }
    void highShelf(double sr,double f,double db){
        double A=std::pow(10.0,db/40.0),w=2*PI*f/sr,c=std::cos(w),s=std::sin(w),beta=std::sqrt(A+A);
        set(A*((A+1)+(A-1)*c+beta*s),-2*A*((A-1)+(A+1)*c),A*((A+1)+(A-1)*c-beta*s),
            (A+1)-(A-1)*c+beta*s,2*((A-1)-(A+1)*c),(A+1)-(A-1)*c-beta*s);
    }
    inline void process(float &l,float &r){
        double yL=b0*l+z1L; z1L=b1*l-a1*yL+z2L; z2L=b2*l-a2*yL; l=(float)yL;
        double yR=b0*r+z1R; z1R=b1*r-a1*yR+z2R; z2R=b2*r-a2*yR; r=(float)yR;
    }
};

struct StretchState {
    bool initialized=false;
    double grainStart=0.0;
    std::array<float, OVERLAP*2> tail{};
    std::array<float, HOP*2> out{};
    int outIndex=HOP;
    void reset(){initialized=false;grainStart=0;outIndex=HOP;tail.fill(0);out.fill(0);}
};


struct BeatGrid {
    double bpm = 120.0;
    double offset = 0.0;
    std::vector<double> beatTimes;

    double beatAt(double time) const {
        if (beatTimes.size() < 2) return (time - offset) * bpm / 60.0;
        int lo = 0, hi = static_cast<int>(beatTimes.size()) - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >> 1;
            if (beatTimes[mid] <= time) lo = mid; else hi = mid;
        }
        double a = beatTimes[lo], b = beatTimes[lo + 1];
        double d = b - a;
        return lo + (std::abs(d) > 1e-9 ? (time - a) / d : 0.0);
    }
};

struct Deck {
    std::shared_ptr<AudioBuffer> buffer;
    std::atomic<BeatGrid*> grid{nullptr};
    std::atomic<bool> playing{false};
    std::atomic<int64_t> pendingStartFrames{-1};
    std::atomic<double> timelineSec{0};
    std::atomic<double> seekRequest{-1};
    std::atomic<double> rate{1.0}, bend{0.0}, correction{0.0};
    std::atomic<bool> keyLock{true};
    std::atomic<double> keySemi{0.0};
    std::atomic<float> trim{1}, volume{1}, lowDb{0}, midDb{0}, highDb{0}, filter{0};
    std::atomic<bool> loopEnabled{false};
    std::atomic<bool> scratching{false};
    std::atomic<bool> resetDspRequested{false};
    std::atomic<double> scratchSec{0.0}, scratchRate{0.0};
    std::atomic<double> loopStart{0}, loopEnd{0};
    StretchState stretch;
    double directPos=0;
    Biquad low,mid,high;
    float cachedLow=999,cachedMid=999,cachedHigh=999;
    float filterLpL=0,filterLpR=0, filterHpInL=0,filterHpInR=0, filterHpOutL=0,filterHpOutR=0;
    float vu=0;
};

struct SampleVoice { std::shared_ptr<AudioBuffer> b; double pos=0; float gain=1; int64_t pendingFrames=0; bool active=false; };
struct RhythmVoice { AudioBuffer* b=nullptr; double pos=0, step=1; float gain=1; int row=0; bool active=false; };
struct LoopSlot { std::shared_ptr<AudioBuffer> b; double pos=0, rate=1, bpm=0; bool playing=false; float gain=1; };

struct Recorder {
    std::atomic<bool> active{false};
    std::vector<float> ring;
    std::atomic<uint64_t> write{0}, read{0};
    FILE* file=nullptr;
    lame_t lame=nullptr;
    std::thread thread;
    std::condition_variable cv;
    std::mutex cvMutex;
    int sr=48000;

    void push(const float* data,int frames){
        if(!active.load(std::memory_order_relaxed) || ring.empty()) return;
        uint64_t w=write.load(std::memory_order_relaxed), r=read.load(std::memory_order_acquire), cap=ring.size();
        uint64_t count=(uint64_t)frames*2;
        if(count > cap-(w-r)) { // drop oldest rather than blocking real-time audio
            read.store(r + (count-(cap-(w-r))), std::memory_order_release);
        }
        for(uint64_t i=0;i<count;i++) ring[(w+i)%cap]=data[i];
        write.store(w+count,std::memory_order_release);
        cv.notify_one();
    }

    bool start(const std::string& path,int sampleRate,int kbps){
        stop(); sr=sampleRate; file=std::fopen(path.c_str(),"wb"); if(!file) return false;
        lame=lame_init(); if(!lame){std::fclose(file);file=nullptr;return false;}
        lame_set_in_samplerate(lame,sr); lame_set_num_channels(lame,2); lame_set_brate(lame,kbps); lame_set_mode(lame,STEREO); lame_set_quality(lame,2);
        if(lame_init_params(lame)<0){lame_close(lame);lame=nullptr;std::fclose(file);file=nullptr;return false;}
        ring.assign((size_t)sr*2*12,0); write=0;read=0;active=true;
        thread=std::thread([this]{ run(); }); return true;
    }
    void run(){
        std::vector<float> pcm(1152*2); std::vector<unsigned char> mp3(16384);
        while(active.load() || read.load()<write.load()){
            uint64_t r=read.load(std::memory_order_relaxed),w=write.load(std::memory_order_acquire); size_t available=(size_t)(w-r);
            if(available<2){std::unique_lock<std::mutex> lk(cvMutex);cv.wait_for(lk,std::chrono::milliseconds(25));continue;}
            int frames=(int)std::min<size_t>(1152,available/2);
            for(int i=0;i<frames*2;i++) pcm[i]=ring[(r+i)%ring.size()];
            read.store(r+(uint64_t)frames*2,std::memory_order_release);
            int n=lame_encode_buffer_interleaved_ieee_float(lame,pcm.data(),frames,mp3.data(),(int)mp3.size());
            if(n>0 && file) std::fwrite(mp3.data(),1,n,file);
        }
        if(lame&&file){int n=lame_encode_flush(lame,mp3.data(),(int)mp3.size());if(n>0)std::fwrite(mp3.data(),1,n,file);}
    }
    void stop(){
        if(active.exchange(false)){cv.notify_all();if(thread.joinable())thread.join();}
        else if(thread.joinable())thread.join();
        if(lame){lame_close(lame);lame=nullptr;} if(file){std::fclose(file);file=nullptr;} ring.clear();
    }
    ~Recorder(){stop();}
};

class Engine {
public:
    Deck decks[2];
    std::mutex dataMutex;
    std::array<std::shared_ptr<AudioBuffer>,SAMPLE_SLOTS> samples{};
    std::array<SampleVoice,MAX_SAMPLE_VOICES> voices{};
    std::array<LoopSlot,12> loopSlots{};
    // Native Rhythm Socket / live drum sequencer. AudioBuffer pointers are immutable
    // once published; old buffers stay owned for engine lifetime so the AAudio callback
    // never locks while a sound selector is changed.
    std::vector<std::shared_ptr<AudioBuffer>> rhythmOwned;
    std::vector<std::unique_ptr<BeatGrid>> gridOwned;
    std::array<std::atomic<AudioBuffer*>,RHYTHM_ROWS> rhythmSamples{};
    std::array<std::atomic<uint16_t>,RHYTHM_ROWS> rhythmPattern{};
    std::array<RhythmVoice,MAX_RHYTHM_VOICES> rhythmVoices{};
    std::atomic<uint32_t> rhythmEnabledMask{(1u<<RHYTHM_ROWS)-1u};
    std::atomic<bool> rhythmActive{false}, rhythmRestartRequested{false};
    std::atomic<float> rhythmLevel{1.0f};
    std::atomic<int> rhythmBarShift{0}, rhythmKey{0}, rhythmCurrentStep{-1};
    bool rhythmStarted=false;
    int rhythmLastStep=INT32_MIN, rhythmLastMaster=-1;
    float rhythmOutputLevel=0.0f;
    float looperGain=1,padVolume=1;
    std::atomic<float> looperFilter{0.0f},looperBassDb{0.0f};
    Biquad looperBass; float cachedLooperBass=999.0f;
    float looperLpL=0,looperLpR=0,looperHpInL=0,looperHpInR=0,looperHpOutL=0,looperHpOutR=0;
    std::atomic<float> cross{0.5f},master{1.0f},masterTrim{1.0f},masterBalance{0.0f},masterFilter{0};
    std::atomic<float> masterLowDb{0},masterMidDb{0},masterHighDb{0};
    std::atomic<int> crossCurve{1},fxType{0};
    std::atomic<bool> fxEnabled{false};
    std::atomic<float> fxTime{.5f},fxDepth{.5f},fxLevel{.5f};
    // Twelve-module native Master FX rack. Module numbers match NativeDjEngine.RACK_* constants.
    std::array<std::atomic<bool>,12> rackOn{};
    std::array<std::atomic<float>,12> rackAmount{};
    std::array<std::atomic<int>,12> rackMode{};
    std::atomic<float> rackWet{.38f},rackBpm{120.0f};
    std::atomic<float> vuL{0},vuR{0};
    AAudioStream* stream=nullptr;
    int outputRate=48000;
    std::vector<float> fxDelay;
    size_t fxWrite=0;
    double fxPhase=0;
    std::array<float,4> phL{},phR{};
    std::vector<float> rackReverbRing,rackDelayRing,rackEchoRing,rackModRing;
    size_t rackReverbWrite=0,rackDelayWrite=0,rackEchoWrite=0,rackModWrite=0;
    double rackChorusPhase=0,rackFlangerPhase=0,rackPhaserPhase=0;
    std::array<float,8> rackPhL{},rackPhR{};
    float rackReverbDampL=0,rackReverbDampR=0,rackCompEnv=0,rackLimiterGain=1;
    float rackFilterLpL=0,rackFilterLpR=0,rackEqLpL=0,rackEqLpR=0;
    float masterLpL=0,masterLpR=0,masterHpInL=0,masterHpInR=0,masterHpOutL=0,masterHpOutR=0;
    Biquad masterLow,masterMid,masterHigh;
    float cachedMasterLow=999,cachedMasterMid=999,cachedMasterHigh=999;
    Recorder recorder;

    ~Engine(){ stop(); recorder.stop(); }

    bool start(){
        if(stream) return true;
        AAudioStreamBuilder* b=nullptr; if(AAudio_createStreamBuilder(&b)!=AAUDIO_OK) return false;
        AAudioStreamBuilder_setDirection(b,AAUDIO_DIRECTION_OUTPUT);
        AAudioStreamBuilder_setFormat(b,AAUDIO_FORMAT_PCM_FLOAT);
        AAudioStreamBuilder_setChannelCount(b,2);
        AAudioStreamBuilder_setPerformanceMode(b,AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
        AAudioStreamBuilder_setSharingMode(b,AAUDIO_SHARING_MODE_EXCLUSIVE);
        AAudioStreamBuilder_setDataCallback(b,dataCallback,this);
        aaudio_result_t r=AAudioStreamBuilder_openStream(b,&stream);
        if(r!=AAUDIO_OK){
            AAudioStreamBuilder_setSharingMode(b,AAUDIO_SHARING_MODE_SHARED);
            r=AAudioStreamBuilder_openStream(b,&stream);
        }
        AAudioStreamBuilder_delete(b);
        if(r!=AAUDIO_OK||!stream){stream=nullptr;LOGE("AAudio open failed %d",r);return false;}
        outputRate=AAudioStream_getSampleRate(stream); if(outputRate<=0) outputRate=48000;
        fxDelay.assign((size_t)outputRate/8*2+8,0);
        rackReverbRing.assign((size_t)outputRate*2*2+8,0);
        rackDelayRing.assign((size_t)outputRate*4*2+8,0);
        rackEchoRing.assign((size_t)outputRate*4*2+8,0);
        rackModRing.assign((size_t)outputRate/8*2+8,0);
        r=AAudioStream_requestStart(stream); if(r!=AAUDIO_OK){AAudioStream_close(stream);stream=nullptr;return false;}
        LOGI("AAudio started at %d Hz",outputRate); return true;
    }
    void stop(){ if(stream){AAudioStream_requestStop(stream);AAudioStream_close(stream);stream=nullptr;} }

    static aaudio_data_callback_result_t dataCallback(AAudioStream*,void* user,void* audio,int32_t frames){
        return static_cast<Engine*>(user)->render((float*)audio,frames);
    }

    aaudio_data_callback_result_t render(float* out,int frames){
        // Never block the real-time audio callback on sample/looper load operations.
        // A single try-lock per callback protects auxiliary voice/loop state. If a load
        // happens at the same instant, only that auxiliary buffer is skipped.
        std::unique_lock<std::mutex> auxLock(dataMutex,std::try_to_lock);
        const bool auxReady=auxLock.owns_lock();
        for(int i=0;i<frames;i++){
            float aL=0,aR=0,bL=0,bR=0;
            renderDeck(decks[0],aL,aR); renderDeck(decks[1],bL,bR);
            float x=clampf(cross.load(std::memory_order_relaxed),0,1),gA,gB;
            int curve=crossCurve.load(std::memory_order_relaxed);
            if(curve==0){gA=1-x;gB=x;} else if(curve==2){gA=std::pow(1-x,.28f);gB=std::pow(x,.28f);} else {gA=std::cos(x*(float)PI*.5f);gB=std::sin(x*(float)PI*.5f);}
            float l=aL*gA+bL*gB, r=aR*gA+bR*gB;
            if(auxReady){renderSamples(l,r);renderLooper(l,r);}
            renderRhythm(l,r);
            processMasterFx(l,r); processRackFx(l,r); processMasterFilter(l,r); processMasterEq(l,r);
            float bal=clampf(masterBalance.load(std::memory_order_relaxed),-1,1);
            if(bal>0) l*=1-bal; else if(bal<0) r*=1+bal;
            float mg=clampf(master.load(std::memory_order_relaxed),0,2)*clampf(masterTrim.load(std::memory_order_relaxed),0,4); l*=mg;r*=mg;
            l=softClip(l);r=softClip(r); out[i*2]=l;out[i*2+1]=r;
            float al=std::abs(l),ar=std::abs(r);vuL.store(std::max(al,vuL.load()*.996f));vuR.store(std::max(ar,vuR.load()*.996f));
        }
        recorder.push(out,frames);
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    static float softClip(float x){ return x/(1.0f+0.22f*std::abs(x)); }

    void resetDeckPosition(Deck& d,double seconds){
        auto b=std::atomic_load_explicit(&d.buffer,std::memory_order_acquire); if(!b) return; seconds=clampd(seconds,0,b->frames()/(double)b->sampleRate);d.timelineSec=seconds;d.directPos=seconds*b->sampleRate;d.stretch.reset();d.low.reset();d.mid.reset();d.high.reset();
    }

    inline void readSource(const Deck& d,const AudioBuffer& b,double pos,double step,float& l,float& r) const {
        int64_t frames=b.frames();
        if(frames<2){l=r=0;return;}
        bool loop=d.loopEnabled.load(std::memory_order_relaxed);double ls=d.loopStart.load()*b.sampleRate, le=d.loopEnd.load()*b.sampleRate;
        if(loop && le>ls+2){double len=le-ls; while(pos>=le)pos-=len;while(pos<ls)pos+=len;}
        if(pos<0||pos>=frames-1){l=r=0;return;}
        int64_t i=(int64_t)pos; float t=(float)(pos-i);const float* p=b.stereo.data();
        l=p[i*2]+(p[(i+1)*2]-p[i*2])*t;r=p[i*2+1]+(p[(i+1)*2+1]-p[i*2+1])*t;
    }

    void ensureEq(Deck& d){
        float lo=d.lowDb.load(),mi=d.midDb.load(),hi=d.highDb.load();
        if(lo!=d.cachedLow){d.low.lowShelf(outputRate,250,lo);d.cachedLow=lo;}
        if(mi!=d.cachedMid){d.mid.peak(outputRate,1200,1.0,mi);d.cachedMid=mi;}
        if(hi!=d.cachedHigh){d.high.highShelf(outputRate,3800,hi);d.cachedHigh=hi;}
    }

    void processDeckFilter(Deck& d,float& l,float& r){
        float f=clampf(d.filter.load(),-1,1);if(std::abs(f)<.005f)return;
        if(f<0){double cutoff=20000*std::pow(80.0/20000.0,-f),a=1-std::exp(-2*PI*cutoff/outputRate);d.filterLpL+=(float)a*(l-d.filterLpL);d.filterLpR+=(float)a*(r-d.filterLpR);l=d.filterLpL;r=d.filterLpR;}
        else {double cutoff=20*std::pow(9000.0/20.0,f),a=std::exp(-2*PI*cutoff/outputRate);float yl=(float)a*(d.filterHpOutL+l-d.filterHpInL),yr=(float)a*(d.filterHpOutR+r-d.filterHpInR);d.filterHpInL=l;d.filterHpInR=r;d.filterHpOutL=yl;d.filterHpOutR=yr;l=yl;r=yr;}
    }

    void renderDeck(Deck& d,float& l,float& r){
        l=r=0; auto b=std::atomic_load_explicit(&d.buffer,std::memory_order_acquire); if(!b)return;
        // All non-atomic DSP state belongs to the audio thread. JNI/UI code only
        // requests a reset; the callback performs it at a safe buffer boundary.
        if(d.resetDspRequested.exchange(false,std::memory_order_acq_rel)){
            d.directPos=d.timelineSec.load(std::memory_order_relaxed)*b->sampleRate;
            d.stretch.reset(); d.low.reset(); d.mid.reset(); d.high.reset();
            d.filterLpL=d.filterLpR=d.filterHpInL=d.filterHpInR=d.filterHpOutL=d.filterHpOutR=0;
        }
        if(!d.playing.load(std::memory_order_relaxed)){
            int64_t pending=d.pendingStartFrames.load(std::memory_order_relaxed);
            if(pending<0) return;
            if(pending>0){ d.pendingStartFrames.fetch_sub(1,std::memory_order_relaxed); return; }
            d.pendingStartFrames.store(-1,std::memory_order_relaxed); d.playing.store(true,std::memory_order_relaxed);
        }
        double seek=d.seekRequest.exchange(-1); if(seek>=0) resetDeckPosition(d,seek);
        if(d.scratching.load(std::memory_order_relaxed)){
            double sec=d.scratchSec.load(std::memory_order_relaxed), rate=d.scratchRate.load(std::memory_order_relaxed);
            readSource(d,*b,sec*b->sampleRate,b->sampleRate/(double)outputRate,l,r);
            sec += rate/outputRate; double dur=b->frames()/(double)b->sampleRate;
            if(d.loopEnabled.load()){double ls=d.loopStart.load(),le=d.loopEnd.load();if(le>ls+.001){while(sec>=le)sec-=le-ls;while(sec<ls)sec+=le-ls;}}
            else sec=clampd(sec,0,std::max(0.0,dur-1.0/b->sampleRate));
            d.scratchSec.store(sec,std::memory_order_relaxed);d.timelineSec.store(sec,std::memory_order_relaxed);
            ensureEq(d);d.low.process(l,r);d.mid.process(l,r);d.high.process(l,r);processDeckFilter(d,l,r);float g=clampf(d.trim.load(),0,4)*clampf(d.volume.load(),0,1.5f);l*=g;r*=g;float pk=std::max(std::abs(l),std::abs(r));d.vu=std::max(pk,d.vu*.996f);return;
        }
        double tempo=clampd(d.rate.load()*(1+d.bend.load())*(1+d.correction.load()),.2,2.0);
        double semi=clampd(d.keySemi.load(),-12,12),keyFactor=std::pow(2.0,semi/12.0);
        bool stretch=d.keyLock.load()||std::abs(semi)>.001;
        if(stretch){
            if(d.stretch.outIndex>=HOP) generateStretch(d,*b,tempo,keyFactor);
            int ix=d.stretch.outIndex++; l=d.stretch.out[ix*2];r=d.stretch.out[ix*2+1];
        } else {
            double ratio=b->sampleRate/(double)outputRate; readSource(d,*b,d.directPos,tempo*ratio,l,r); d.directPos+=tempo*ratio;
        }
        double next=d.timelineSec.load()+tempo/outputRate; double dur=b->frames()/(double)b->sampleRate;
        if(d.loopEnabled.load()){double ls=d.loopStart.load(),le=d.loopEnd.load();if(le>ls+.001&&next>=le){next=ls+std::fmod(next-ls,le-ls);d.directPos=next*b->sampleRate;d.stretch.reset();}}
        else if(next>=dur){next=dur;d.playing=false;}
        d.timelineSec.store(next,std::memory_order_relaxed);
        ensureEq(d);d.low.process(l,r);d.mid.process(l,r);d.high.process(l,r);processDeckFilter(d,l,r);
        float g=clampf(d.trim.load(),0,4)*clampf(d.volume.load(),0,1.5f);l*=g;r*=g;float pk=std::max(std::abs(l),std::abs(r));d.vu=std::max(pk,d.vu*.996f);
    }

    void buildGrain(const Deck& d,const AudioBuffer& b,double start,double sourceStep,std::array<float,GRAIN*2>& grain){
        for(int i=0;i<GRAIN;i++){float l,r;readSource(d,b,start+i*sourceStep,sourceStep,l,r);grain[i*2]=l;grain[i*2+1]=r;}
    }

    void generateStretch(Deck& d,const AudioBuffer& b,double tempo,double pitchFactor){
        auto& s=d.stretch; double ratio=b.sampleRate/(double)outputRate; double sourceStep=ratio*pitchFactor;
        std::array<float,GRAIN*2> grain{};
        if(!s.initialized){
            double start=d.timelineSec.load()*b.sampleRate;buildGrain(d,b,start,sourceStep,grain);for(int i=0;i<HOP*2;i++)s.out[i]=grain[i];for(int i=0;i<OVERLAP;i++){s.tail[i*2]=grain[(i+HOP)*2];s.tail[i*2+1]=grain[(i+HOP)*2+1];}
            s.grainStart=start;s.initialized=true;s.outIndex=0;return;
        }
        double expected=s.grainStart + HOP*tempo*ratio;
        double bestStart=expected,best=-1e30;
        for(int off=-SEARCH;off<=SEARCH;off+=4){double cand=expected+off*ratio;double score=0,normA=1e-9,normB=1e-9;for(int i=0;i<CORR;i+=3){float l,r;readSource(d,b,cand+i*sourceStep,sourceStep,l,r);float a=(s.tail[i*2]+s.tail[i*2+1])*.5f,bb=(l+r)*.5f;score+=a*bb;normA+=a*a;normB+=bb*bb;}score/=std::sqrt(normA*normB);if(score>best){best=score;bestStart=cand;}}
        buildGrain(d,b,bestStart,sourceStep,grain);
        std::array<float,OVERLAP*2> blend{};
        for(int i=0;i<OVERLAP;i++){float a=(float)i/(OVERLAP-1);float wa=std::cos(a*(float)PI*.5f);wa*=wa;float wb=1-wa;blend[i*2]=s.tail[i*2]*wa+grain[i*2]*wb;blend[i*2+1]=s.tail[i*2+1]*wa+grain[i*2+1]*wb;}
        for(int i=0;i<HOP;i++){s.out[i*2]=blend[i*2];s.out[i*2+1]=blend[i*2+1];}
        for(int i=0;i<OVERLAP-HOP;i++){s.tail[i*2]=blend[(i+HOP)*2];s.tail[i*2+1]=blend[(i+HOP)*2+1];}
        for(int i=OVERLAP-HOP;i<OVERLAP;i++){int gi=i+HOP;s.tail[i*2]=grain[gi*2];s.tail[i*2+1]=grain[gi*2+1];}
        s.grainStart=bestStart;s.outIndex=0;
    }

    void renderSamples(float& l,float& r){
        // dataMutex is held once by render().
        for(auto& v:voices){
            if(!v.active||!v.b)continue;
            if(v.pendingFrames>0){--v.pendingFrames;continue;}
            auto& b=*v.b;double ratio=b.sampleRate/(double)outputRate;if(v.pos>=b.frames()-1){v.active=false;continue;}int64_t i=(int64_t)v.pos;float t=(float)(v.pos-i);float sl=b.stereo[i*2]+(b.stereo[(i+1)*2]-b.stereo[i*2])*t;float sr=b.stereo[i*2+1]+(b.stereo[(i+1)*2+1]-b.stereo[i*2+1])*t;float g=v.gain*padVolume;l+=sl*g;r+=sr*g;v.pos+=ratio;
        }
    }
    void renderLooper(float& l,float& r){
        // dataMutex is held once by render(). Twelve slots match the packaged Groove rack.
        float ml=0.0f,mr=0.0f;
        for(auto& slot:loopSlots){if(!slot.playing||!slot.b||slot.b->frames()<2)continue;auto& b=*slot.b;double step=b.sampleRate/(double)outputRate*slot.rate;while(slot.pos>=b.frames())slot.pos-=b.frames();while(slot.pos<0)slot.pos+=b.frames();int64_t i=(int64_t)slot.pos;int64_t j=(i+1)%b.frames();float t=(float)(slot.pos-i);float g=slot.gain;ml+=(b.stereo[i*2]+(b.stereo[j*2]-b.stereo[i*2])*t)*g;mr+=(b.stereo[i*2+1]+(b.stereo[j*2+1]-b.stereo[i*2+1])*t)*g;slot.pos+=step;}
        float bass=looperBassDb.load(std::memory_order_relaxed);if(bass!=cachedLooperBass){looperBass.lowShelf(outputRate,180,bass);cachedLooperBass=bass;}looperBass.process(ml,mr);
        float f=clampf(looperFilter.load(std::memory_order_relaxed),-1,1);
        if(f<-.005f){double cutoff=20000*std::pow(80.0/20000.0,-f),a=1-std::exp(-2*PI*cutoff/outputRate);looperLpL+=(float)a*(ml-looperLpL);looperLpR+=(float)a*(mr-looperLpR);ml=looperLpL;mr=looperLpR;}
        else if(f>.005f){double cutoff=20*std::pow(9000.0/20.0,f),a=std::exp(-2*PI*cutoff/outputRate);float yl=(float)a*(looperHpOutL+ml-looperHpInL),yr=(float)a*(looperHpOutR+mr-looperHpInR);looperHpInL=ml;looperHpInR=mr;looperHpOutL=yl;looperHpOutR=yr;ml=yl;mr=yr;}
        float g=clampf(looperGain,0,2);l+=ml*g;r+=mr*g;
    }


    static int imod(int n,int d){int r=n%d;return r<0?r+d:r;}

    int rhythmMasterDeck() const {
        const bool a=decks[0].playing.load(std::memory_order_relaxed);
        const bool b=decks[1].playing.load(std::memory_order_relaxed);
        if(a&&!b)return 0;
        if(b&&!a)return 1;
        if(a&&b)return cross.load(std::memory_order_relaxed)<=.5f?0:1;
        return -1;
    }

    double rhythmBeatAt(int deck,double sourceSeconds) const {
        BeatGrid* g=decks[deck].grid.load(std::memory_order_acquire);
        if(g)return g->beatAt(sourceSeconds);
        return sourceSeconds*2.0; // 120 BPM fallback; normal track analysis supplies a grid.
    }

    void triggerRhythmRow(int row){
        if(row<0||row>=RHYTHM_ROWS)return;
        AudioBuffer* b=rhythmSamples[row].load(std::memory_order_acquire);
        if(!b||b->frames()<2)return;
        RhythmVoice* target=nullptr;
        for(auto& v:rhythmVoices)if(!v.active){target=&v;break;}
        if(!target)target=&rhythmVoices[0];
        const double pitch=std::pow(2.0,clampd(rhythmKey.load(std::memory_order_relaxed),-12,12)/12.0);
        target->b=b;target->pos=0;target->step=b->sampleRate/(double)outputRate*pitch;
        target->gain=1.0f;target->row=row;target->active=true;
    }

    void renderRhythm(float& l,float& r){
        if(rhythmRestartRequested.exchange(false,std::memory_order_acq_rel)){
            rhythmStarted=false;rhythmLastStep=INT32_MIN;rhythmCurrentStep.store(-1,std::memory_order_relaxed);
        }
        int masterDeck=rhythmMasterDeck();
        const bool playing=masterDeck>=0;
        if(masterDeck!=rhythmLastMaster){rhythmLastMaster=masterDeck;rhythmLastStep=INT32_MIN;}
        double beat=0.0;
        int step=0;
        bool crossed=false;
        if(playing){
            beat=rhythmBeatAt(masterDeck,decks[masterDeck].timelineSec.load(std::memory_order_relaxed));
            step=(int)std::floor(beat*4.0+1e-8);
            crossed=rhythmLastStep!=INT32_MIN && step!=rhythmLastStep;
        }
        const bool active=rhythmActive.load(std::memory_order_relaxed);
        if(active&&playing&&crossed){
            if(!rhythmStarted&&imod(step,16)==0)rhythmStarted=true;
            if(rhythmStarted){
                const int column=imod(step+rhythmBarShift.load(std::memory_order_relaxed),RHYTHM_STEPS);
                const uint32_t enabled=rhythmEnabledMask.load(std::memory_order_relaxed);
                for(int row=0;row<RHYTHM_ROWS;row++){
                    if(!(enabled&(1u<<row)))continue;
                    const uint16_t mask=rhythmPattern[row].load(std::memory_order_relaxed);
                    if(mask&(uint16_t)(1u<<column))triggerRhythmRow(row);
                }
            }
        }
        rhythmLastStep=playing?step:INT32_MIN;
        rhythmCurrentStep.store(active&&rhythmStarted&&playing?imod(step+rhythmBarShift.load(std::memory_order_relaxed),16):-1,std::memory_order_relaxed);

        const float target=active&&playing?clampf(rhythmLevel.load(std::memory_order_relaxed),0,1):0.0f;
        const float gateAlpha=1.0f-std::exp(-1.0f/(std::max(1,outputRate)*.004f));
        rhythmOutputLevel+=(target-rhythmOutputLevel)*gateAlpha;
        const uint32_t enabled=rhythmEnabledMask.load(std::memory_order_relaxed);
        const double pitch=std::pow(2.0,clampd(rhythmKey.load(std::memory_order_relaxed),-12,12)/12.0);
        const float pitchAlpha=1.0f-std::exp(-1.0f/(std::max(1,outputRate)*.012f));

        for(auto& v:rhythmVoices){
            if(!v.active||!v.b)continue;
            AudioBuffer& b=*v.b;
            if(v.pos>=b.frames()-1){v.active=false;continue;}
            if(!(enabled&(1u<<v.row))||!active||!playing)v.gain*=.98f;
            int64_t i=(int64_t)v.pos;float f=(float)(v.pos-i);
            float sl=b.stereo[i*2]+(b.stereo[(i+1)*2]-b.stereo[i*2])*f;
            float sr=b.stereo[i*2+1]+(b.stereo[(i+1)*2+1]-b.stereo[i*2+1])*f;
            l+=sl*v.gain*rhythmOutputLevel;r+=sr*v.gain*rhythmOutputLevel;
            const double wanted=b.sampleRate/(double)outputRate*pitch;
            v.step+=(wanted-v.step)*pitchAlpha;
            v.pos+=v.step;
            if(v.pos>=b.frames()-1||v.gain<1e-5f)v.active=false;
        }
    }

    void processMasterFilter(float& l,float& r){float f=clampf(masterFilter.load(),-1,1);if(std::abs(f)<.005)return;if(f<0){double cutoff=20000*std::pow(80.0/20000.0,-f),a=1-std::exp(-2*PI*cutoff/outputRate);masterLpL+=(float)a*(l-masterLpL);masterLpR+=(float)a*(r-masterLpR);l=masterLpL;r=masterLpR;}else{double cutoff=20*std::pow(9000.0/20.0,f),a=std::exp(-2*PI*cutoff/outputRate);float yl=(float)a*(masterHpOutL+l-masterHpInL),yr=(float)a*(masterHpOutR+r-masterHpInR);masterHpInL=l;masterHpInR=r;masterHpOutL=yl;masterHpOutR=yr;l=yl;r=yr;}}

    void processMasterEq(float& l,float& r){
        float lo=masterLowDb.load(std::memory_order_relaxed),mi=masterMidDb.load(std::memory_order_relaxed),hi=masterHighDb.load(std::memory_order_relaxed);
        if(lo!=cachedMasterLow){masterLow.lowShelf(outputRate,180,lo);cachedMasterLow=lo;}
        if(mi!=cachedMasterMid){masterMid.peak(outputRate,1100,0.9,mi);cachedMasterMid=mi;}
        if(hi!=cachedMasterHigh){masterHigh.highShelf(outputRate,5200,hi);cachedMasterHigh=hi;}
        masterLow.process(l,r);masterMid.process(l,r);masterHigh.process(l,r);
    }

    inline float ringRead(const std::vector<float>& ring,size_t write,double delayFrames,int channel) const {
        if(ring.size()<8)return 0;size_t cap=ring.size()/2;double d=clampd(delayFrames,1.0,(double)cap-3.0);
        double rp=(double)write-d;while(rp<0)rp+=cap;while(rp>=cap)rp-=cap;size_t i=(size_t)rp,j=(i+1)%cap;float f=(float)(rp-i);
        return ring[i*2+channel]+(ring[j*2+channel]-ring[i*2+channel])*f;
    }
    inline void ringWrite(std::vector<float>& ring,size_t& write,float l,float r){if(ring.size()<8)return;size_t cap=ring.size()/2;ring[write*2]=l;ring[write*2+1]=r;write=(write+1)%cap;}

    void processRackFx(float& l,float& r){
        const float global=clampf(rackWet.load(std::memory_order_relaxed),0,1);if(global<=.0001f)return;
        const float bpm=clampf(rackBpm.load(std::memory_order_relaxed),40,250);const double beat=outputRate*60.0/bpm;
        // Reverb: compact damped feedback network, with room characters matching HALL/ROOM/PLATE/AMBIENCE.
        if(rackOn[0].load(std::memory_order_relaxed)&&!rackReverbRing.empty()){
            float amount=clampf(rackAmount[0].load(),0,1),mix=global*amount;int mode=rackMode[0].load();
            double d1=outputRate*(mode==1?.041:mode==2?.029:mode==3?.067:.079),d2=outputRate*(mode==1?.063:mode==2?.047:mode==3?.113:.127);
            float tL=ringRead(rackReverbRing,rackReverbWrite,d1,0)+.61f*ringRead(rackReverbRing,rackReverbWrite,d2,1);
            float tR=ringRead(rackReverbRing,rackReverbWrite,d1*1.07,1)+.61f*ringRead(rackReverbRing,rackReverbWrite,d2*.93,0);
            float damp=.08f+.18f*(1-amount);rackReverbDampL+=damp*(tL-rackReverbDampL);rackReverbDampR+=damp*(tR-rackReverbDampR);
            float fb=mode==1?.48f:mode==2?.56f:mode==3?.66f:.72f;ringWrite(rackReverbRing,rackReverbWrite,l+rackReverbDampL*fb,r+rackReverbDampR*fb);
            l=l*(1-mix)+rackReverbDampL*.55f*mix;r=r*(1-mix)+rackReverbDampR*.55f*mix;
        }
        // Delay and echo divisions are the exact option order from the 3.4 rack UI.
        if(rackOn[1].load(std::memory_order_relaxed)&&!rackDelayRing.empty()){
            static const double divs[5]={.125,.25,.5,1,2};int mode=std::max(0,std::min(4,rackMode[1].load()));float amount=clampf(rackAmount[1].load(),0,1),mix=global*amount;double d=beat*divs[mode];
            float dl=ringRead(rackDelayRing,rackDelayWrite,d,0),dr=ringRead(rackDelayRing,rackDelayWrite,d,1);ringWrite(rackDelayRing,rackDelayWrite,l+dl*.28f,r+dr*.28f);l=l*(1-mix)+dl*mix;r=r*(1-mix)+dr*mix;
        }
        if(rackOn[2].load(std::memory_order_relaxed)&&!rackEchoRing.empty()){
            static const double divs[5]={.0625,.125,.25,.5,1};int mode=std::max(0,std::min(4,rackMode[2].load()));float amount=clampf(rackAmount[2].load(),0,1),mix=global*amount;double d=beat*divs[mode];
            float dl=ringRead(rackEchoRing,rackEchoWrite,d,0),dr=ringRead(rackEchoRing,rackEchoWrite,d,1);ringWrite(rackEchoRing,rackEchoWrite,l+dr*.24f,r+dl*.24f);l=l*(1-mix)+dr*mix;r=r*(1-mix)+dl*mix;
        }
        // The modulation history always advances so enabling chorus/flanger is click-minimized.
        if(!rackModRing.empty()){
            float sourceL=l,sourceR=r;
            if(rackOn[3].load(std::memory_order_relaxed)){
                float amount=clampf(rackAmount[3].load(),0,1),mix=global*amount;int mode=std::max(0,std::min(2,rackMode[3].load()));double rate=mode==1?.38:mode==2?.22:.28,depth=mode==1?.0045:mode==2?.008:.006,base=mode==2?.018:.013;rackChorusPhase+=rate/outputRate;if(rackChorusPhase>=1)rackChorusPhase-=1;
                double dl=outputRate*(base+depth*(.5+.5*std::sin(2*PI*rackChorusPhase))),dr=outputRate*(base+depth*(.5+.5*std::sin(2*PI*(rackChorusPhase+.27))));float wl=ringRead(rackModRing,rackModWrite,dl,0),wr=ringRead(rackModRing,rackModWrite,dr,1);l=l*(1-mix)+wl*mix;r=r*(1-mix)+wr*mix;
            }
            if(rackOn[4].load(std::memory_order_relaxed)){
                float amount=clampf(rackAmount[4].load(),0,1),mix=global*amount;int mode=std::max(0,std::min(2,rackMode[4].load()));double rate=mode==1?.24:mode==2?.35:.50,depthMs=mode==1?2.5:mode==2?8.0:5.0;rackFlangerPhase+=rate/outputRate;if(rackFlangerPhase>=1)rackFlangerPhase-=1;double d=outputRate*.001*(.35+depthMs*(.5+.5*std::sin(2*PI*rackFlangerPhase)));float wl=ringRead(rackModRing,rackModWrite,d,0),wr=ringRead(rackModRing,rackModWrite,d*1.07,1);l=l*(1-mix)+wl*mix;r=r*(1-mix)+wr*mix;
            }
            ringWrite(rackModRing,rackModWrite,sourceL,sourceR);
        }
        if(rackOn[5].load(std::memory_order_relaxed)){
            float amount=clampf(rackAmount[5].load(),0,1),mix=global*amount;int mode=std::max(0,std::min(2,rackMode[5].load()));int stages=mode==0?8:mode==1?4:2;double rate=mode==2?.45:mode==1?.75:.8;rackPhaserPhase+=rate/outputRate;if(rackPhaserPhase>=1)rackPhaserPhase-=1;float a=.12f+.78f*(float)(.5+.5*std::sin(2*PI*rackPhaserPhase)),wl=l,wr=r;for(int k=0;k<stages;k++){float yl=-a*wl+rackPhL[k];rackPhL[k]=wl+a*yl;wl=yl;float yr=-a*wr+rackPhR[k];rackPhR[k]=wr+a*yr;wr=yr;}l=l*(1-mix)+wl*mix;r=r*(1-mix)+wr*mix;
        }
        if(rackOn[6].load(std::memory_order_relaxed)){
            float amount=clampf(rackAmount[6].load(),0,1),mix=global*amount;int mode=std::max(0,std::min(2,rackMode[6].load()));float drive=mode==2?1+amount*13:mode==1?1+amount*4.5f:1+amount*7.5f,n=std::tanh(drive);float wl=std::tanh(l*drive)/std::max(.001f,n),wr=std::tanh(r*drive)/std::max(.001f,n);l=l*(1-mix)+wl*mix;r=r*(1-mix)+wr*mix;
        }
        if(rackOn[7].load(std::memory_order_relaxed)){
            float amount=clampf(rackAmount[7].load(),0,1),mix=global*amount;int mode=std::max(0,std::min(2,rackMode[7].load()));float threshold=mode==1?-16:mode==2?-12:-14,ratio=mode==1?3:2;double attackMs=mode==1?8:mode==2?25:15,releaseMs=mode==1?130:mode==2?220:180;float det=std::max(std::abs(l),std::abs(r));float a=(float)std::exp(-1.0/(outputRate*(det>rackCompEnv?attackMs:releaseMs)/1000.0));rackCompEnv=a*rackCompEnv+(1-a)*det;float db=20*std::log10(std::max(1e-9f,rackCompEnv));float over=std::max(0.0f,db-threshold),grDb=-over*(1-1/ratio),g=dbGain(grDb);l*=1-mix+mix*g;r*=1-mix+mix*g;
        }
        if(rackOn[8].load(std::memory_order_relaxed)){
            float amount=clampf(rackAmount[8].load(),0,1),ceiling=dbGain(-.5f-2.5f*amount),peak=std::max(std::abs(l),std::abs(r)),need=std::min(1.0f,ceiling/std::max(1e-9f,peak));int mode=std::max(0,std::min(2,rackMode[8].load()));float rel=mode==2?.0025f:mode==1?.00055f:.0011f;if(need<rackLimiterGain)rackLimiterGain=need;else rackLimiterGain+=rel*(need-rackLimiterGain);l*=rackLimiterGain;r*=rackLimiterGain;
        }
        if(rackOn[9].load(std::memory_order_relaxed)){
            float amount=clampf(rackAmount[9].load(),0,1),mix=global*amount;int mode=std::max(0,std::min(2,rackMode[9].load()));if(mode==1){double fc=20*std::pow(100.0,amount),a=1-std::exp(-2*PI*std::min(outputRate*.45,fc)/outputRate);rackFilterLpL+=(float)a*(l-rackFilterLpL);rackFilterLpR+=(float)a*(r-rackFilterLpR);l=l*(1-mix)+(l-rackFilterLpL)*mix;r=r*(1-mix)+(r-rackFilterLpR)*mix;}else if(mode==2){double fc=400*std::pow(8.0,amount),a=1-std::exp(-2*PI*std::min(outputRate*.45,fc)/outputRate);rackFilterLpL+=(float)a*(l-rackFilterLpL);rackFilterLpR+=(float)a*(r-rackFilterLpR);float ml=l-rackFilterLpL,mr=r-rackFilterLpR;l=l*(1-mix*.35f)+(rackFilterLpL*.45f+ml*.8f)*mix*.35f;r=r*(1-mix*.35f)+(rackFilterLpR*.45f+mr*.8f)*mix*.35f;}else{double fc=20000*std::pow(.02,amount),a=1-std::exp(-2*PI*std::min(outputRate*.45,fc)/outputRate);rackFilterLpL+=(float)a*(l-rackFilterLpL);rackFilterLpR+=(float)a*(r-rackFilterLpR);l=l*(1-mix)+rackFilterLpL*mix;r=r*(1-mix)+rackFilterLpR*mix;}
        }
        if(rackOn[10].load(std::memory_order_relaxed)){float amount=clampf(rackAmount[10].load(),0,1);int mode=std::max(0,std::min(1,rackMode[10].load()));float width=mode==0?1+amount:1-.75f*amount,mid=(l+r)*.5f,side=(l-r)*.5f*width;l=mid+side;r=mid-side;}
        if(rackOn[11].load(std::memory_order_relaxed)){float amount=clampf(rackAmount[11].load(),0,1),aMix=global*std::min(.45f,amount/2.2f);double a=1-std::exp(-2*PI*220/outputRate);rackEqLpL+=(float)a*(l-rackEqLpL);rackEqLpR+=(float)a*(r-rackEqLpR);float ll=rackEqLpL,lr=rackEqLpR,hl=l-ll,hr=r-lr;int mode=std::max(0,std::min(2,rackMode[11].load()));if(mode==1){l+=hl*aMix;r+=hr*aMix;}else if(mode==2){l+=ll*aMix*.9f-hl*aMix*.08f;r+=lr*aMix*.9f-hr*aMix*.08f;}else{l+=ll*aMix*.55f-hl*aMix*.12f;r+=lr*aMix*.55f-hr*aMix*.12f;}}
    }

    void processMasterFx(float& l,float& r){if(!fxEnabled.load())return;int type=fxType.load();float depth=clampf(fxDepth.load(),0,1),level=clampf(fxLevel.load(),0,1),time=clampf(fxTime.load(),0,1);float wetL=l,wetR=r;
        if(type==0 && !fxDelay.empty()){double lfo=(std::sin(fxPhase)+1)*.5;double delayMs=.3+(1.0+time*9.0)*(.25+.75*lfo);size_t delayFrames=(size_t)(delayMs*.001*outputRate);size_t cap=fxDelay.size()/2,read=(fxWrite+cap-delayFrames%cap)%cap;wetL=fxDelay[read*2];wetR=fxDelay[read*2+1];fxDelay[fxWrite*2]=l;fxDelay[fxWrite*2+1]=r;fxWrite=(fxWrite+1)%cap;fxPhase+=2*PI*(.08+time*1.5)/outputRate;if(fxPhase>2*PI)fxPhase-=2*PI;}
        else if(type==1){float a=.15f+.75f*(float)((std::sin(fxPhase)+1)*.5);for(int k=0;k<4;k++){float yL=-a*wetL+phL[k];phL[k]=wetL+a*yL;wetL=yL;float yR=-a*wetR+phR[k];phR[k]=wetR+a*yR;wetR=yR;}fxPhase+=2*PI*(.05+time*.9)/outputRate;if(fxPhase>2*PI)fxPhase-=2*PI;}
        else if(type==2){double hz=.5+time*12.0;float gate=std::sin(fxPhase)>0?1.0f:(1.0f-depth);wetL=l*gate;wetR=r*gate;fxPhase+=2*PI*hz/outputRate;if(fxPhase>2*PI)fxPhase-=2*PI;}
        float mix=level*depth;l=l*(1-mix)+wetL*mix;r=r*(1-mix)+wetR*mix;
    }
};

inline Engine* E(jlong h){return reinterpret_cast<Engine*>(h);} 
inline int di(jint d){return d==1?1:0;}
std::shared_ptr<AudioBuffer> fromArray(JNIEnv* env,jfloatArray arr,jint sr){if(!arr)return{};jsize n=env->GetArrayLength(arr);if(n<2)return{};auto b=std::make_shared<AudioBuffer>();b->stereo.resize(n);env->GetFloatArrayRegion(arr,0,n,b->stereo.data());b->sampleRate=sr>0?sr:44100;return b;}
std::string jstr(JNIEnv* env,jstring s){if(!s)return{};const char* p=env->GetStringUTFChars(s,nullptr);std::string out=p?p:"";if(p)env->ReleaseStringUTFChars(s,p);return out;}
}

extern "C" JNIEXPORT jlong JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nCreate(JNIEnv*,jclass){return reinterpret_cast<jlong>(new Engine());}
extern "C" JNIEXPORT jboolean JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nStart(JNIEnv*,jclass,jlong h){return E(h)&&E(h)->start();}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nStop(JNIEnv*,jclass,jlong h){if(E(h))E(h)->stop();}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nDestroy(JNIEnv*,jclass,jlong h){delete E(h);}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nLoadDeck(JNIEnv* env,jclass,jlong h,jint d,jfloatArray a,jint sr){auto e=E(h);if(!e)return;auto b=fromArray(env,a,sr);auto& x=e->decks[di(d)];x.playing=false;x.pendingStartFrames=-1;x.timelineSec=0;x.seekRequest=-1;x.resetDspRequested=true;std::atomic_store_explicit(&x.buffer,b,std::memory_order_release);}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nUnloadDeck(JNIEnv*,jclass,jlong h,jint d){auto e=E(h);if(!e)return;e->decks[di(d)].playing=false;std::atomic_store_explicit(&e->decks[di(d)].buffer,std::shared_ptr<AudioBuffer>{},std::memory_order_release);}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetPlaying(JNIEnv*,jclass,jlong h,jint d,jboolean v){if(E(h)){auto& x=E(h)->decks[di(d)];x.pendingStartFrames=-1;x.playing=v;}}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSchedulePlay(JNIEnv*,jclass,jlong h,jint d,jdouble sec){if(E(h)){auto& x=E(h)->decks[di(d)];x.playing=false;x.pendingStartFrames=(int64_t)std::llround(std::max(0.0,(double)sec)*E(h)->outputRate);}}
extern "C" JNIEXPORT jboolean JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nIsPlaying(JNIEnv*,jclass,jlong h,jint d){return E(h)&&E(h)->decks[di(d)].playing.load();}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSeek(JNIEnv*,jclass,jlong h,jint d,jdouble s){if(E(h))E(h)->decks[di(d)].seekRequest=s;}
extern "C" JNIEXPORT jdouble JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nPosition(JNIEnv*,jclass,jlong h,jint d){return E(h)?E(h)->decks[di(d)].timelineSec.load():0;}
extern "C" JNIEXPORT jdouble JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nAudiblePosition(JNIEnv*,jclass,jlong h,jint d){return E(h)?E(h)->decks[di(d)].timelineSec.load():0;}
extern "C" JNIEXPORT jdouble JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nDuration(JNIEnv*,jclass,jlong h,jint d){auto e=E(h);if(!e)return 0;auto b=std::atomic_load_explicit(&e->decks[di(d)].buffer,std::memory_order_acquire);if(!b)return 0;return b->frames()/(double)b->sampleRate;}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetPlaybackRate(JNIEnv*,jclass,jlong h,jint d,jdouble v){if(E(h))E(h)->decks[di(d)].rate=clampd(v,.2,2);}
extern "C" JNIEXPORT jdouble JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nPlaybackRate(JNIEnv*,jclass,jlong h,jint d){return E(h)?E(h)->decks[di(d)].rate.load():1;}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetPitchBend(JNIEnv*,jclass,jlong h,jint d,jdouble v){if(E(h))E(h)->decks[di(d)].bend=clampd(v,-.2,.2);}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetPhaseCorrection(JNIEnv*,jclass,jlong h,jint d,jdouble v){if(E(h))E(h)->decks[di(d)].correction=clampd(v,-.02,.02);}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetKeyLock(JNIEnv*,jclass,jlong h,jint d,jboolean v){if(E(h)){auto& x=E(h)->decks[di(d)];x.keyLock=v;x.resetDspRequested=true;}}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetKeySemitones(JNIEnv*,jclass,jlong h,jint d,jdouble v){if(E(h)){auto& x=E(h)->decks[di(d)];x.keySemi=clampd(v,-12,12);x.resetDspRequested=true;}}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetTrim(JNIEnv*,jclass,jlong h,jint d,jfloat v){if(E(h))E(h)->decks[di(d)].trim=clampf(v,0,4);}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetVolume(JNIEnv*,jclass,jlong h,jint d,jfloat v){if(E(h))E(h)->decks[di(d)].volume=clampf(v,0,1.5f);}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetEq(JNIEnv*,jclass,jlong h,jint d,jfloat lo,jfloat mi,jfloat hi){if(E(h)){auto& x=E(h)->decks[di(d)];x.lowDb=clampf(lo,-26,6);x.midDb=clampf(mi,-26,6);x.highDb=clampf(hi,-26,6);}}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetFilter(JNIEnv*,jclass,jlong h,jint d,jfloat v){if(E(h))E(h)->decks[di(d)].filter=clampf(v,-1,1);}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetLoop(JNIEnv*,jclass,jlong h,jint d,jboolean on,jdouble s,jdouble en){if(E(h)){auto& x=E(h)->decks[di(d)];x.loopStart=std::max(0.0,(double)s);x.loopEnd=std::max((double)s,(double)en);x.loopEnabled=on;}}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nBeginScratch(JNIEnv*,jclass,jlong h,jint d){auto e=E(h);if(!e)return;auto& x=e->decks[di(d)];x.scratchSec=x.timelineSec.load();x.scratchRate=0;x.resetDspRequested=true;x.scratching=true;}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nMoveScratch(JNIEnv*,jclass,jlong h,jint d,jdouble sec,jdouble rate){auto e=E(h);if(!e)return;auto& x=e->decks[di(d)];x.scratchSec=std::max(0.0,(double)sec);x.scratchRate=clampd(rate,-4,4);}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nEndScratch(JNIEnv*,jclass,jlong h,jint d){auto e=E(h);if(!e)return;auto& x=e->decks[di(d)];double sec=x.scratchSec.load();x.scratchRate=0;x.timelineSec=sec;x.seekRequest=sec;x.resetDspRequested=true;x.scratching=false;}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetCrossfader(JNIEnv*,jclass,jlong h,jfloat v){if(E(h))E(h)->cross=clampf(v,0,1);}
extern "C" JNIEXPORT jfloat JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nCrossfader(JNIEnv*,jclass,jlong h){return E(h)?E(h)->cross.load():.5f;}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetCrossfaderCurve(JNIEnv*,jclass,jlong h,jint v){if(E(h))E(h)->crossCurve=std::max(0,std::min(2,(int)v));}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetMasterLevel(JNIEnv*,jclass,jlong h,jfloat v){if(E(h))E(h)->master=clampf(v,0,2);}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetMasterTrim(JNIEnv*,jclass,jlong h,jfloat v){if(E(h))E(h)->masterTrim=clampf(v,0,4);}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetMasterBalance(JNIEnv*,jclass,jlong h,jfloat v){if(E(h))E(h)->masterBalance=clampf(v,-1,1);}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetMasterEq(JNIEnv*,jclass,jlong h,jfloat lo,jfloat mi,jfloat hi){if(E(h)){E(h)->masterLowDb=clampf(lo,-26,6);E(h)->masterMidDb=clampf(mi,-26,6);E(h)->masterHighDb=clampf(hi,-26,6);}}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetMasterFilter(JNIEnv*,jclass,jlong h,jfloat v){if(E(h))E(h)->masterFilter=clampf(v,-1,1);}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetMasterFx(JNIEnv*,jclass,jlong h,jint t,jboolean on,jfloat time,jfloat depth,jfloat level){if(E(h)){E(h)->fxType=std::max(0,std::min(2,(int)t));E(h)->fxEnabled=on;E(h)->fxTime=clampf(time,0,1);E(h)->fxDepth=clampf(depth,0,1);E(h)->fxLevel=clampf(level,0,1);}}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetRackFx(JNIEnv*,jclass,jlong h,jint module,jboolean on,jfloat amount,jint mode){auto e=E(h);if(!e||module<0||module>=12)return;e->rackOn[module]=on;e->rackAmount[module]=clampf(amount,0,1);e->rackMode[module]=std::max(0,(int)mode);}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetRackFxWet(JNIEnv*,jclass,jlong h,jfloat wet){if(E(h))E(h)->rackWet=clampf(wet,0,1);}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetRackFxTempo(JNIEnv*,jclass,jlong h,jfloat bpm){if(E(h))E(h)->rackBpm=clampf(bpm,40,250);}
extern "C" JNIEXPORT jfloat JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nVuLeft(JNIEnv*,jclass,jlong h){return E(h)?E(h)->vuL.load():0;}
extern "C" JNIEXPORT jfloat JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nVuRight(JNIEnv*,jclass,jlong h){return E(h)?E(h)->vuR.load():0;}
extern "C" JNIEXPORT jfloat JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nVuDeck(JNIEnv*,jclass,jlong h,jint d){return E(h)?E(h)->decks[di(d)].vu:0;}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nLoadSample(JNIEnv* env,jclass,jlong h,jint slot,jfloatArray a,jint sr){auto e=E(h);if(!e||slot<0||slot>=SAMPLE_SLOTS)return;auto b=fromArray(env,a,sr);std::lock_guard<std::mutex> l(e->dataMutex);e->samples[slot]=b;}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nClearSample(JNIEnv*,jclass,jlong h,jint slot){auto e=E(h);if(!e||slot<0||slot>=SAMPLE_SLOTS)return;std::lock_guard<std::mutex> l(e->dataMutex);e->samples[slot].reset();}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nTriggerSample(JNIEnv*,jclass,jlong h,jint slot,jfloat gain){auto e=E(h);if(!e||slot<0||slot>=SAMPLE_SLOTS)return;std::lock_guard<std::mutex> l(e->dataMutex);if(!e->samples[slot])return;SampleVoice* target=nullptr;for(auto& v:e->voices)if(!v.active){target=&v;break;}if(!target)target=&e->voices[0];target->b=e->samples[slot];target->pos=0;target->gain=clampf(gain,0,2);target->pendingFrames=0;target->active=true;}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nTriggerSampleScheduled(JNIEnv*,jclass,jlong h,jint slot,jfloat gain,jdouble delaySec){auto e=E(h);if(!e||slot<0||slot>=SAMPLE_SLOTS)return;std::lock_guard<std::mutex> l(e->dataMutex);if(!e->samples[slot])return;SampleVoice* target=nullptr;for(auto& v:e->voices)if(!v.active){target=&v;break;}if(!target)target=&e->voices[0];target->b=e->samples[slot];target->pos=0;target->gain=clampf(gain,0,2);target->pendingFrames=(int64_t)std::llround(std::max(0.0,(double)delaySec)*e->outputRate);target->active=true;}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetPadVolume(JNIEnv*,jclass,jlong h,jfloat v){if(E(h))E(h)->padVolume=clampf(v,0,2);}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nLoadLooperSlot(JNIEnv* env,jclass,jlong h,jint slot,jfloatArray a,jint sr,jdouble bpm){auto e=E(h);if(!e||slot<0||slot>=12)return;auto b=fromArray(env,a,sr);std::lock_guard<std::mutex> l(e->dataMutex);auto& x=e->loopSlots[slot];x.b=b;x.bpm=bpm;x.pos=0;x.rate=1;x.playing=false;}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nStartLooperSlot(JNIEnv*,jclass,jlong h,jint slot,jdouble target,jdouble start){auto e=E(h);if(!e||slot<0||slot>=12)return;std::lock_guard<std::mutex> l(e->dataMutex);auto& x=e->loopSlots[slot];if(!x.b)return;x.rate=(x.bpm>0&&target>0)?target/x.bpm:1;x.pos=std::max(0.0,(double)start)*x.b->sampleRate;x.playing=true;}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nStopLooperSlot(JNIEnv*,jclass,jlong h,jint slot){auto e=E(h);if(!e||slot<0||slot>=12)return;std::lock_guard<std::mutex> l(e->dataMutex);e->loopSlots[slot].playing=false;}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nClearLooperSlot(JNIEnv*,jclass,jlong h,jint slot){auto e=E(h);if(!e||slot<0||slot>=12)return;std::lock_guard<std::mutex> l(e->dataMutex);e->loopSlots[slot]=LoopSlot{};}
extern "C" JNIEXPORT jboolean JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nIsLooperSlotPlaying(JNIEnv*,jclass,jlong h,jint slot){auto e=E(h);if(!e||slot<0||slot>=12)return false;std::lock_guard<std::mutex> l(e->dataMutex);return e->loopSlots[slot].playing;}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetLooperGain(JNIEnv*,jclass,jlong h,jfloat v){if(E(h))E(h)->looperGain=clampf(v,0,2);}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetLooperTone(JNIEnv*,jclass,jlong h,jfloat filter,jfloat bassDb){auto e=E(h);if(!e)return;e->looperFilter.store(clampf(filter,-1,1),std::memory_order_relaxed);e->looperBassDb.store(clampf(bassDb,-12,6),std::memory_order_relaxed);}

extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetDeckBeatGrid(JNIEnv* env,jclass,jlong h,jint deck,jdouble bpm,jdouble offset,jdoubleArray times){
    auto e=E(h);if(!e)return;auto g=std::make_unique<BeatGrid>();g->bpm=clampd(bpm,20,400);g->offset=offset;
    if(times){jsize n=env->GetArrayLength(times);if(n>0){g->beatTimes.resize(n);env->GetDoubleArrayRegion(times,0,n,g->beatTimes.data());}}
    BeatGrid* raw=g.get();std::lock_guard<std::mutex> lock(e->dataMutex);e->gridOwned.push_back(std::move(g));e->decks[di(deck)].grid.store(raw,std::memory_order_release);
}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nLoadRhythmRow(JNIEnv* env,jclass,jlong h,jint row,jfloatArray a,jint sr){
    auto e=E(h);if(!e||row<0||row>=RHYTHM_ROWS)return;auto b=fromArray(env,a,sr);if(!b)return;AudioBuffer* raw=b.get();std::lock_guard<std::mutex> lock(e->dataMutex);e->rhythmOwned.push_back(b);e->rhythmSamples[row].store(raw,std::memory_order_release);
}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetRhythmPattern(JNIEnv* env,jclass,jlong h,jintArray masks,jint enabledMask,jfloat level){
    auto e=E(h);if(!e)return;jint local[RHYTHM_ROWS]{};if(masks){jsize n=std::min<jsize>(RHYTHM_ROWS,env->GetArrayLength(masks));env->GetIntArrayRegion(masks,0,n,local);}
    for(int r=0;r<RHYTHM_ROWS;r++)e->rhythmPattern[r].store((uint16_t)(local[r]&0xFFFF),std::memory_order_relaxed);
    e->rhythmEnabledMask.store((uint32_t)enabledMask&((1u<<RHYTHM_ROWS)-1u),std::memory_order_relaxed);e->rhythmLevel.store(clampf(level,0,1),std::memory_order_relaxed);
}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetRhythmActive(JNIEnv*,jclass,jlong h,jboolean active){auto e=E(h);if(!e)return;e->rhythmActive.store(active,std::memory_order_relaxed);e->rhythmRestartRequested.store(true,std::memory_order_release);}
extern "C" JNIEXPORT jboolean JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nRhythmActive(JNIEnv*,jclass,jlong h){return E(h)&&E(h)->rhythmActive.load(std::memory_order_relaxed);}
extern "C" JNIEXPORT jint JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nRhythmCurrentStep(JNIEnv*,jclass,jlong h){return E(h)?E(h)->rhythmCurrentStep.load(std::memory_order_relaxed):-1;}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nSetRhythmKey(JNIEnv*,jclass,jlong h,jint semi){if(E(h))E(h)->rhythmKey.store(std::max(-12,std::min(12,(int)semi)),std::memory_order_relaxed);}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nShiftRhythm(JNIEnv*,jclass,jlong h,jint steps){auto e=E(h);if(!e)return;int v=e->rhythmBarShift.load(std::memory_order_relaxed);e->rhythmBarShift.store(Engine::imod(v+steps,16),std::memory_order_relaxed);e->rhythmRestartRequested.store(true,std::memory_order_release);}

extern "C" JNIEXPORT jboolean JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nStartRecording(JNIEnv* env,jclass,jlong h,jstring p,jint kbps){auto e=E(h);return e&&e->recorder.start(jstr(env,p),e->outputRate,kbps);}
extern "C" JNIEXPORT void JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nStopRecording(JNIEnv*,jclass,jlong h){if(E(h))E(h)->recorder.stop();}
extern "C" JNIEXPORT jboolean JNICALL Java_com_djiman_bugobi_advance34_audio_NativeDjEngine_nIsRecording(JNIEnv*,jclass,jlong h){return E(h)&&E(h)->recorder.active.load();}
