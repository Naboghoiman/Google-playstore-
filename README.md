# DJ IMAN 3.4 — Pure Native Android Port

This project is a source-level native Android reconstruction of `DJ-IMAN-3.4-Live-Performance-Kit.apk`.

## Architecture

The original APK used a native Android host around a packaged web application (`assets/www`, WebView, JavaScript/WebAudio and WASM). This project deliberately removes that execution architecture.

The native port uses:

- Java 17 Android application code and a custom `View` for the supplied interface artwork.
- `MediaStore` for the device music library.
- `MediaExtractor`/`MediaCodec` decoding to PCM.
- C++20 + AAudio for the low-latency two-deck render/mixer path.
- Native DSP for transport, tempo/key processing, EQ/filtering, crossfader, master processing, sampler, looper and effects.
- Native LAME encoding for MP3 mix recording.
- No `WebView`, no HTML UI, no CSS, no JavaScript runtime and no WebAudio runtime.

The Android package ID is preserved as `com.djiman.bugobi.advance34`.

## Build requirements

- Android Studio with Android SDK 35
- Android Gradle Plugin 8.9.2 compatible Gradle/JDK setup
- NDK with CMake 3.22.1 support
- JDK 17 or newer capable of targeting Java 17

Open this directory as an Android Studio project, allow Gradle/SDK components to sync, and build the `app` module. The project targets `arm64-v8a` and `armeabi-v7a` and requires Android 8.0 / API 26 or newer because the audio path uses AAudio.

This execution environment does not contain an Android SDK or Gradle installation, so a signed APK cannot be truthfully produced here. `native_engine.cpp` has been host syntax-checked with Android/AAudio header stubs; that is not a substitute for an Android Gradle/NDK build.

## Rhythm/Groove checkpoint

The current checkpoint includes a native Rhythm Socket driven from the same analysed source-coordinate beat grids as the decks. It preserves the packaged 3.4 next-bar start rule, dynamic-master clock, 16-step drum pattern, live sound selectors/presets, persistence, and portrait-app/sideways-sequencer presentation. The Groove rack now exposes 12 native loop slots with persisted user loop loading plus native Filter, Bass and Volume controls. See `NATIVE_PORT_STATUS.md` for the remaining exact-parity items.

## Preservation rule

Reference PNG artwork, bundled factory sampler audio, package identity, portrait orientation, opening-code verifier semantics and the current 3.4 interaction model are treated as the migration specification. Implementation internals necessarily differ because browser APIs have been replaced by Android/AAudio APIs.

See `NATIVE_PORT_STATUS.md` for the exact current parity state.
