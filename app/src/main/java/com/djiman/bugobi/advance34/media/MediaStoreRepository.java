package com.djiman.bugobi.advance34.media;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import com.djiman.bugobi.advance34.model.TrackInfo;

import java.util.ArrayList;
import java.util.List;

/** Native MediaStore library. No file upload or browser bridge is involved. */
public final class MediaStoreRepository {
    private final ContentResolver resolver;

    public MediaStoreRepository(Context context) { resolver = context.getContentResolver(); }

    public List<TrackInfo> queryAll() {
        ArrayList<TrackInfo> result = new ArrayList<>();
        Uri root = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_MODIFIED
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";
        try (Cursor c = resolver.query(root, projection, selection, null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            if (c == null) return result;
            int id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int size = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);
            int modified = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED);
            while (c.moveToNext()) {
                long mediaId = c.getLong(id);
                Uri uri = ContentUris.withAppendedId(root, mediaId);
                result.add(new TrackInfo(uri,
                        nz(c.getString(title), "Unknown title"),
                        nz(c.getString(artist), "Unknown artist"),
                        nz(c.getString(album), ""),
                        c.getLong(duration), c.getLong(size), c.getLong(modified)));
            }
        }
        return result;
    }

    private static String nz(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }
}
