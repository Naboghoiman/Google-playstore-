# Native Port Status — checkpoint

This file intentionally separates implemented native behavior from work that still needs parity testing or implementation. It should not be read as a claim that the whole APK is already feature-identical.

## Native and implemented

- Pure Android launcher/activity; portrait lock; original package ID.
- Original mixer/deck/master/looper/reference artwork copied into Android resources.
- No WebView/HTML/CSS/JavaScript/WebAudio runtime in `app/src/main`.
- MediaStore track browsing and Android PCM decoding.
- Two native deck buffers rendered by AAudio.
- Play/pause, CUE and hold-CUE + PLAY latch behavior.
- Native tempo, pitch bend, key lock and semitone key change.
- Native scratch transport and auto-scratch controller.
- Native beat/BPM analysis and grid refinement.
- Half/double BPM-family normalization and dynamic-master synchronization controller.
- Continuous phase correction/sync lock.
- Crossfader plus linear/smooth/scratch curve support.
- Per-deck trim, channel volume, 3-band EQ and filter.
- Native VU levels.
- Native sampler slots with bundled 3.4 samples, user loading, persistence, pad volume, mute and beat-quantized triggering.
- Twelve native Groove loop slots matching the packaged rack, local loop restoration, BPM adaptation and automatic re-anchoring when the dynamic master changes.
- Native Groove Filter, Bass and Volume controls; the Groove KEY control also drives the Rhythm Socket key as in the packaged 3.4 routing.
- Native MP3 recording through bundled LAME.
- Master trim, output level, balance, filter and 3-band master EQ.
- Mixer quick Master FX: flanger, phaser and cut.
- Native twelve-module Master FX rack with the original rack coordinates and module order: Reverb, Delay, Echo, Chorus, Flanger, Phaser, Distortion, Compressor, Limiter, Filter, Stereo Width, EQ Color.
- Master FX rack power, module power, amount knobs, style/division cycling, dry/wet, master-output control, and original rack presets: Club Standard, Vocal Clean, Wide Hall, Dry Tight.
- Original private-test opening-code verification algorithm ported to Android (PBKDF2/HMAC-SHA256 verifier semantics and lockout behavior).
- Automix queue/controller and native transition path.
- Real-time safety improvement: deck DSP resets are requested from JNI but executed on the AAudio callback; sample/looper state uses one callback-level try-lock instead of per-sample blocking.

## Still requires native parity work before calling the whole APK complete

- Full Dynamics rack parity: all compressor, multiband, limiter, clipper/maximizer, gate/denoise, oversampling and preset controls visible in the original screen.
- Full 31-band master EQ screen, draw/zoom/preset behavior and presence control. Current native master EQ is 3-band only.
- Complete master-output processor screen parity for every control and preset.
- Full headphone/PFL/split-cue signal routing parity.
- Full native Rhythm Socket sequencer is implemented: 16 visible drum/percussion families mapped to the original 18 internal rows, 16-step patterns, row enable/mute, next-bar start, dynamic-master beat-grid clock, 32-voice cap, drum key shift, sound selectors, the original five sound presets, SAVE/CLEAR/RESET persistence and sideways landscape presentation inside the portrait app.
- Groove rack pad/key parity still needs one advanced item: pitch-preserving key shift for arbitrary uploaded Groove loops. The Rhythm Socket itself follows Groove KEY already.
- Groove loop beat-count/manual-beats editor and the original loop-sound rerender quality still require final parity work.
- Any deck FX panels beyond the quick/master paths already implemented.
- Complete advanced sampler/performance-room recording/edit controls present in the web build.
- Detailed visual/touch verification on every supported Android aspect ratio. The primary mixer/deck artwork is preserved; some secondary reference boards have different source aspect ratios and still need layout-specific native treatment.
- Device validation for MediaCodec format coverage, AAudio underruns, long-track memory use, latency calibration and actual beat-sync accuracy across a large song library.
- Full Android Gradle/NDK compilation, installation, signing and on-device regression testing. Android SDK/Gradle are not present in this workspace.

## Verification performed in this workspace

- C++20 syntax check of `native_engine.cpp`: passes; only deprecation warnings are emitted by the host libstdc++ for legacy atomic shared_ptr helper functions.
- Java 17 parser check passes for the modified controller, native bridge, main custom View, Rhythm Controller and Rhythm Kit.
- All 90 synthesized Rhythm Kit sound variants render finite PCM in the host-side sound-bank test; five original kit presets are present.
- JNI declaration/function-pair audit passes for the new beat-grid, Rhythm Socket and Groove tone bridge methods.
- Source scan confirms no runtime `android.webkit`, `WebView`, `loadUrl()` or packaged `assets/www` implementation in the native app module.
- UI navigation conflict on the Master FX power control was fixed; Android Back returns rack screens to Master Output and then to the mixer.
