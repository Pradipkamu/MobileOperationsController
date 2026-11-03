package com.mobileops.controller;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.UserInfo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import android.util.Base64;

/**
 * Strict SSH host-key pinning for the configured Pradipweb endpoint.
 *
 * JSch calls check() with the raw SSH host public-key blob. A connection is
 * accepted only when the SHA-256 fingerprint matches the value the user
 * independently verified and saved in the app.
 */
public final class PinnedHostKeyRepository implements HostKeyRepository {
    private final String expectedHost;
    private final String expectedFingerprint;

    public PinnedHostKeyRepository(String expectedHost, String expectedFingerprint) {
        if (expectedHost == null || expectedHost.trim().isEmpty()) {
            throw new IllegalArgumentException("Expected host is required");
        }
        if (expectedFingerprint == null
                || !expectedFingerprint.matches("SHA256:[A-Za-z0-9+/]{20,}={0,2}")) {
            throw new IllegalArgumentException("A valid SHA256 host fingerprint is required");
        }
        this.expectedHost = expectedHost;
        this.expectedFingerprint = expectedFingerprint.replaceAll("=+$", "");
    }

    @Override
    public int check(String host, byte[] key) {
        if (host == null || !expectedHost.equalsIgnoreCase(host) || key == null) {
            return CHANGED;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key);
            String actual = "SHA256:" + Base64.encodeToString(digest, Base64.NO_WRAP | Base64.NO_PADDING);
            byte[] expectedBytes = expectedFingerprint.getBytes(StandardCharsets.US_ASCII);
            byte[] actualBytes = actual.getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expectedBytes, actualBytes) ? OK : CHANGED;
        } catch (Exception unavailable) {
            // Fail closed: cryptographic errors must never allow an SSH connection.
            return CHANGED;
        }
    }

    @Override public void add(HostKey hostkey, UserInfo ui) {
        // Never learn or persist an unverified key automatically.
    }

    @Override public void remove(String host, String type) { }
    @Override public void remove(String host, String type, byte[] key) { }
    @Override public String getKnownHostsRepositoryID() { return "MobileOperationsController-pinned-host"; }
    @Override public HostKey[] getHostKey() { return new HostKey[0]; }
    @Override public HostKey[] getHostKey(String host, String type) { return new HostKey[0]; }
}
