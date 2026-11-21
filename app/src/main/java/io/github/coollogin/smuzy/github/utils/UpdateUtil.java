package io.github.coollogin.smuzy.github.utils;

import io.github.coollogin.smuzy.BuildConfig;
import io.github.coollogin.smuzy.github.models.LatestRelease;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateUtil {
    private static final Pattern VERSION_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)*");

    public static boolean showUpdateDialog(LatestRelease release) {
        if (release == null) {
            return false;
        }

        int[] local = parseVersion(BuildConfig.VERSION_NAME);
        int[] remote = parseVersion(release.getTagName());
        if (local == null || remote == null) {
            return false;
        }

        int length = Math.max(local.length, remote.length);
        for (int i = 0; i < length; i++) {
            int localPart = i < local.length ? local[i] : 0;
            int remotePart = i < remote.length ? remote[i] : 0;

            if (localPart != remotePart) {
                return localPart < remotePart;
            }
        }

        return false;
    }

    private static int[] parseVersion(String rawVersion) {
        if (rawVersion == null) {
            return null;
        }

        Matcher matcher = VERSION_PATTERN.matcher(rawVersion);
        if (!matcher.find()) {
            return null;
        }

        String[] parts = matcher.group().split("\\.");
        int[] version = new int[parts.length];

        try {
            for (int i = 0; i < parts.length; i++) {
                version[i] = Integer.parseInt(parts[i]);
            }
        } catch (NumberFormatException exception) {
            return null;
        }

        return version;
    }
}
