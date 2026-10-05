package com.djiman.bugobi.advance34.media;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import com.djiman.bugobi.advance34.model.PcmTrack;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Native Android MediaExtractor/MediaCodec decoder used instead of decodeAudioData/WebAudio.
 * Produces interleaved stereo float PCM. Mono sources are duplicated to stereo.
 */
public final class PcmDecoder {
    private static final long TIMEOUT_US = 10_000;
    private PcmDecoder() {}

    public static PcmTrack decode(Context context, Uri uri) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(context, uri, null);
            int audioTrack = -1;
            MediaFormat inputFormat = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { audioTrack = i; inputFormat = f; break; }
            }
            if (audioTrack < 0 || inputFormat == null) throw new IllegalArgumentException("No audio stream in file");
            extractor.selectTrack(audioTrack);
            String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            if (mime == null) throw new IllegalArgumentException("Audio MIME is missing");
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(inputFormat, null, null, 0);
            codec.start();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            ByteArrayOutputStream pcm16 = new ByteArrayOutputStream(8 * 1024 * 1024);
            FloatBuilder pcmFloat = new FloatBuilder(2 * 1024 * 1024);
            boolean inputDone = false, outputDone = false;
            int sampleRate = inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
            int channels = inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
            int encoding = AudioFormat.ENCODING_PCM_16BIT;

            while (!outputDone) {
                if (!inputDone) {
                    int index = codec.dequeueInputBuffer(TIMEOUT_US);
                    if (index >= 0) {
                        ByteBuffer in = codec.getInputBuffer(index);
                        if (in == null) throw new IllegalStateException("Decoder input buffer unavailable");
                        int n = extractor.readSampleData(in, 0);
                        if (n < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(index, 0, n, extractor.getSampleTime(), extractor.getSampleFlags());
                            extractor.advance();
                        }
                    }
                }

                int outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US);
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat out = codec.getOutputFormat();
                    if (out.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (out.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = out.getInteger(MediaFormat.KEY_PCM_ENCODING);
                } else if (outIndex >= 0) {
                    ByteBuffer out = codec.getOutputBuffer(outIndex);
                    if (out != null && info.size > 0) {
                        out.position(info.offset);
                        out.limit(info.offset + info.size);
                        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                            ByteBuffer b = out.slice().order(ByteOrder.nativeOrder());
                            while (b.remaining() >= 4) pcmFloat.add(b.getFloat());
                        } else {
                            byte[] bytes = new byte[info.size];
                            out.get(bytes);
                            pcm16.write(bytes);
                        }
                    }
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(outIndex, false);
                }
            }

            float[] interleaved;
            if (pcmFloat.size() > 0) {
                interleaved = toStereo(pcmFloat.toArray(), channels);
            } else {
                byte[] data = pcm16.toByteArray();
                int samples = data.length / 2;
                float[] source = new float[samples];
                ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < samples; i++) source[i] = b.getShort() / 32768f;
                interleaved = toStereo(source, channels);
            }
            if (interleaved.length == 0) throw new IllegalStateException("Decoder produced no PCM samples");
            return new PcmTrack(interleaved, sampleRate);
        } finally {
            try { extractor.release(); } catch (Exception ignored) {}
            if (codec != null) {
                try { codec.stop(); } catch (Exception ignored) {}
                try { codec.release(); } catch (Exception ignored) {}
            }
        }
    }

    private static float[] toStereo(float[] source, int channels) {
        channels = Math.max(1, channels);
        int frames = source.length / channels;
        float[] stereo = new float[frames * 2];
        if (channels == 1) {
            for (int i = 0; i < frames; i++) stereo[i * 2] = stereo[i * 2 + 1] = source[i];
        } else {
            for (int i = 0; i < frames; i++) {
                stereo[i * 2] = source[i * channels];
                stereo[i * 2 + 1] = source[i * channels + 1];
            }
        }
        return stereo;
    }

    private static final class FloatBuilder {
        private float[] data; private int size;
        FloatBuilder(int initial) { data = new float[Math.max(1024, initial)]; }
        void add(float x) { if (size == data.length) { float[] n = new float[data.length + data.length / 2]; System.arraycopy(data, 0, n, 0, size); data = n; } data[size++] = x; }
        int size() { return size; }
        float[] toArray() { float[] n = new float[size]; System.arraycopy(data, 0, n, 0, size); return n; }
    }
}
