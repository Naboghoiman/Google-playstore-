package com.djiman.bugobi.advance34.model;

import android.net.Uri;

public final class TrackInfo {
    public final long id;
    public final String title;
    public final String artist;
    public final String album;
    public final long durationMs;
    public final long sizeBytes;
    public final long modifiedSeconds;
    public final Uri uri;

    public TrackInfo(long id, String title, String artist, String album, long durationMs,
                     long sizeBytes, long modifiedSeconds, Uri uri) {
        this.id = id;
        this.title = title == null || title.isBlank() ? "Unknown track" : title;
        this.artist = artist == null || artist.isBlank() ? "Phone audio" : artist;
        this.album = album == null ? "" : album;
        this.durationMs = durationMs;
        this.sizeBytes = sizeBytes;
        this.modifiedSeconds = modifiedSeconds;
        this.uri = uri;
    }

    public TrackInfo(Uri uri, String title, String artist, String album, long durationMs,
                     long sizeBytes, long modifiedSeconds) {
        this(extractId(uri), title, artist, album, durationMs, sizeBytes, modifiedSeconds, uri);
    }

    private static long extractId(Uri uri) {
        if (uri == null) return -1;
        try { return Long.parseLong(uri.getLastPathSegment()); } catch (Exception ignored) { return -1; }
    }

    public String stableKey() {
        return id + ":" + durationMs + ":" + sizeBytes + ":" + modifiedSeconds + ":native34a1";
    }
}
