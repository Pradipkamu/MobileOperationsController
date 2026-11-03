package com.mobileops.controller;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Properties;

/** SSH client that permits only explicitly enumerated, read-only diagnostics. */
public final class PradipwebSshClient {
    public enum Check {
        HOSTNAME,
        DOCKER_STATUS,
        BACKEND_HEALTH,
        FRONTEND_HEALTH
    }

    private static final String HOST = "137.23.40.188";
    private static final String USER = "ubuntu";
    private static final int PORT = 22;
    private static final int TIMEOUT_MS = 10000;
    private static final int MAX_OUTPUT_BYTES = 32768;

    private PradipwebSshClient() { }

    public static String run(byte[] privateKey, String fingerprint, Check check)
            throws Exception {
        if (privateKey == null || privateKey.length == 0) {
            throw new IllegalArgumentException("Import the SSH private key first");
        }
        if (fingerprint == null || fingerprint.trim().isEmpty()) {
            throw new IllegalArgumentException("Save the independently verified host fingerprint first");
        }

        JSch jsch = new JSch();
        jsch.setHostKeyRepository(new PinnedHostKeyRepository(HOST, fingerprint));
        jsch.addIdentity("mobile-operations-key", privateKey, null, null);

        Session session = null;
        ChannelExec channel = null;
        try {
            session = jsch.getSession(USER, HOST, PORT);
            Properties config = new Properties();
            config.setProperty("StrictHostKeyChecking", "yes");
            config.setProperty("PreferredAuthentications", "publickey");
            session.setConfig(config);
            session.setTimeout(TIMEOUT_MS);
            session.connect(TIMEOUT_MS);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(commandFor(check));
            channel.setInputStream(null);
            channel.setErrStream(null);
            InputStream stream = channel.getInputStream();
            channel.connect(TIMEOUT_MS);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            long deadline = System.currentTimeMillis() + TIMEOUT_MS;
            while (true) {
                while (stream.available() > 0) {
                    int count = stream.read(buffer);
                    if (count < 0) break;
                    if (output.size() + count > MAX_OUTPUT_BYTES) {
                        throw new IllegalStateException("Server response exceeded the safety limit");
                    }
                    output.write(buffer, 0, count);
                }
                if (channel.isClosed()) break;
                if (System.currentTimeMillis() > deadline) {
                    throw new java.net.SocketTimeoutException("SSH command timed out");
                }
                Thread.sleep(80);
            }
            int exit = channel.getExitStatus();
            if (exit != 0) {
                throw new IllegalStateException("Read-only server check failed (exit " + exit + ")");
            }
            return output.toString("UTF-8").trim();
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }

    private static String commandFor(Check check) {
        if (check == null) throw new IllegalArgumentException("Select a server check");
        switch (check) {
            case HOSTNAME:
                return "hostname";
            case DOCKER_STATUS:
                return "docker ps --format '{{.Names}}|{{.Status}}|{{.State}}'";
            case BACKEND_HEALTH:
                return "curl --fail --silent --show-error --max-time 5 http://127.0.0.1:8000/health";
            case FRONTEND_HEALTH:
                return "curl --fail --silent --show-error --max-time 5 http://127.0.0.1:5173/";
            default:
                throw new IllegalArgumentException("Unsupported server check");
        }
    }
}
