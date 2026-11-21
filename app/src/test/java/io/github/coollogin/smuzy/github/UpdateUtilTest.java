package io.github.coollogin.smuzy.github;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.github.coollogin.smuzy.github.models.LatestRelease;
import io.github.coollogin.smuzy.github.utils.UpdateUtil;

import org.junit.Test;

public class UpdateUtilTest {

    private static LatestRelease releaseWithTag(String tag) {
        LatestRelease release = new LatestRelease();
        release.setTagName(tag);
        return release;
    }

    @Test
    public void sameVersionDoesNotShowDialog() {
        assertFalse(UpdateUtil.showUpdateDialog(releaseWithTag("v1.0.0")));
    }

    @Test
    public void newerPatchVersionShowsDialog() {
        assertTrue(UpdateUtil.showUpdateDialog(releaseWithTag("v1.0.1")));
    }

    @Test
    public void newerMinorVersionShowsDialog() {
        assertTrue(UpdateUtil.showUpdateDialog(releaseWithTag("v1.1.0")));
    }

    @Test
    public void olderVersionDoesNotShowDialog() {
        assertFalse(UpdateUtil.showUpdateDialog(releaseWithTag("v0.9.9")));
    }

    @Test
    public void shorterTagIsComparedWithZeroPadding() {
        assertFalse(UpdateUtil.showUpdateDialog(releaseWithTag("v1.0")));
    }

    @Test
    public void taggedSuffixIsIgnored() {
        assertTrue(UpdateUtil.showUpdateDialog(releaseWithTag("v1.0.1-beta")));
    }

    @Test
    public void prefixedTagIsParsed() {
        assertTrue(UpdateUtil.showUpdateDialog(releaseWithTag("smuzy-v1.1.0")));
    }

    @Test
    public void missingTagDoesNotShowDialog() {
        assertFalse(UpdateUtil.showUpdateDialog(releaseWithTag(null)));
    }

    @Test
    public void nullReleaseDoesNotShowDialog() {
        assertFalse(UpdateUtil.showUpdateDialog(null));
    }
}
