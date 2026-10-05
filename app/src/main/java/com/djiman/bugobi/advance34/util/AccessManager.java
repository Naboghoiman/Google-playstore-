package com.djiman.bugobi.advance34.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Exact native equivalent of the supplied APK's private-test opening-code verifier. */
public final class AccessManager {
    private static final String PREF = "djiman.native.access";
    private static final String KEY_ID = "dj-iman-private-test-1";
    private static final byte[] SALT = Base64.decode("O+0eGI0DrFXMMtJsrFDcwg==", Base64.DEFAULT);
    private static final int ITERATIONS = 120_000;
    private static final byte[] VERIFIER = hex("2e0a321978b2c2476f6db64f90bf85da1426308582719a064b0c1a74d66e54de");
    private static final Pattern FORMAT = Pattern.compile("^IMAN[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{16}$");
    private static final long BLOCK_MS = 30_000L;

    private final SharedPreferences prefs;

    public AccessManager(Context context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public int remainingSeconds() {
        long until = Math.min(System.currentTimeMillis() + BLOCK_MS,
                Math.max(0L, prefs.getLong("blockedUntil", 0L)));
        long remain = until - System.currentTimeMillis();
        return remain <= 0 ? 0 : (int) Math.ceil(remain / 1000.0);
    }

    public boolean restored() {
        try {
            if (prefs.getInt("version", 0) != 1) return false;
            if (!KEY_ID.equals(prefs.getString("keyId", ""))) return false;
            String b64 = prefs.getString("proof", null);
            if (b64 == null) return false;
            byte[] proof = Base64.decode(b64, Base64.DEFAULT);
            return proof.length == 32 && matches(proof);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public Result redeem(String rawCode, boolean remember) throws Exception {
        int remain = remainingSeconds();
        if (remain > 0) throw new AccessException("Try again in " + remain + " seconds.");
        String code = normalize(rawCode);
        if (!FORMAT.matcher(code).matches()) return failure();

        PBEKeySpec spec = new PBEKeySpec(code.toCharArray(), SALT, ITERATIONS, 256);
        byte[] derived;
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            derived = factory.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
        if (!matches(derived)) return failure();

        SharedPreferences.Editor ed = prefs.edit()
                .putInt("failures", 0)
                .putLong("blockedUntil", 0L);
        if (remember) {
            ed.putInt("version", 1)
                    .putString("provider", "test-code")
                    .putString("keyId", KEY_ID)
                    .putString("proof", Base64.encodeToString(derived, Base64.NO_WRAP));
        } else {
            ed.remove("version").remove("provider").remove("keyId").remove("proof");
        }
        if (!ed.commit() && remember) {
            throw new AccessException("Access could not be saved. Turn off \"Remember access\" and try again.");
        }
        return new Result(true, remember);
    }

    public void forgetPersistentAccess() {
        prefs.edit().remove("version").remove("provider").remove("keyId").remove("proof").apply();
    }

    private Result failure() throws AccessException {
        int failures = Math.min(5, Math.max(0, prefs.getInt("failures", 0))) + 1;
        long blockedUntil = 0L;
        if (failures >= 5) {
            blockedUntil = System.currentTimeMillis() + BLOCK_MS;
            failures = 0;
        }
        prefs.edit().putInt("failures", failures).putLong("blockedUntil", blockedUntil).apply();
        int remaining = remainingSeconds();
        if (remaining > 0) throw new AccessException("Too many attempts. Try again in " + remaining + " seconds.");
        throw new AccessException("That code is not valid. Check it and try again.");
    }

    private static String normalize(String code) {
        if (code == null) return "";
        String n = Normalizer.normalize(code, Normalizer.Form.NFKC).toUpperCase(Locale.ROOT);
        return n.replaceAll("[\\s\\-\\u2010-\\u2015]", "");
    }

    private static boolean matches(byte[] proof) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(proof);
        return MessageDigest.isEqual(digest, VERIFIER);
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    public static final class Result {
        public final boolean granted;
        public final boolean persistent;
        Result(boolean granted, boolean persistent) { this.granted = granted; this.persistent = persistent; }
    }

    public static final class AccessException extends Exception {
        public AccessException(String message) { super(message); }
    }
}
