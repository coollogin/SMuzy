package io.github.coollogin.smuzy.glide;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Navidrome serves a bundled placeholder when an entity exists but has no artwork.
 * The app prefers its own SMuzy placeholders, so these responses must be treated as failures.
 */
final class NavidromePlaceholderDetector {
    // Current Navidrome (>= 0.61) serves placeholders with "Cache-Control: no-store"
    // and no validators; older servers cache the bundled file like normal artwork.
    private static final String[] KNOWN_SHA256 = {
            // artist-placeholder.webp (all versions)
            "521259c652af6d43cb124876106258d93b6b6f7fbe7ad2c73e8345875afe4dcf",
            // album-placeholder.webp (master)
            "273a4dbd61dfbb0a12d5d8ffe780eb7a3d4d000bc9771c5411cc70ae4dfa8a1f",
            // album-placeholder.webp (v0.54.x and earlier)
            "b502979c464ab7a61f79bfd820e23b8dc3eeb36034b0722f7588972fd9fa7392",
    };
    private static final long[] KNOWN_SIZES = {546L, 69228L, 17464L};

    private NavidromePlaceholderDetector() {
    }

    static boolean isPlaceholderResponse(String encodedPath, String cacheControl, String etag,
                                         String lastModified, String contentType) {
        if (!isCoverArtPath(encodedPath)) {
            return false;
        }
        if (etag != null && !etag.isEmpty()) {
            return false;
        }
        if (lastModified != null && !lastModified.isEmpty()) {
            return false;
        }
        if (cacheControl == null
                || !cacheControl.toLowerCase(Locale.ROOT).contains("no-store")) {
            return false;
        }
        return contentType != null
                && contentType.toLowerCase(Locale.ROOT).startsWith("image/webp");
    }

    static boolean isCoverArtPath(String encodedPath) {
        if (encodedPath == null) {
            return false;
        }
        return encodedPath.endsWith("getCoverArt") || encodedPath.endsWith("getCoverArt.view");
    }

    /** Buffering is only worth it when the payload size matches a known placeholder. */
    static boolean shouldBuffer(long contentLength) {
        for (long size : KNOWN_SIZES) {
            if (size == contentLength) {
                return true;
            }
        }
        return false;
    }

    static boolean isKnownPlaceholder(byte[] data) {
        return data != null && shouldBuffer(data.length) && isKnownDigest(sha256Hex(data));
    }

    static boolean isKnownDigest(String sha256) {
        for (String known : KNOWN_SHA256) {
            if (known.equals(sha256)) {
                return true;
            }
        }
        return false;
    }

    static String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 is unavailable", e);
        }
    }
}
