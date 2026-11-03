package com.mobileops.controller;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Properties;
import java.security.Security;

/** SSH client exposing only fixed, read-only server diagnostics. */
public final class PradipwebSshClient {
    public enum Check { HOSTNAME, DOCKER_STATUS, SYSTEM_OVERVIEW }

    private static final String HOST = "137.23.40.188";
    private static final String USER = "ubuntu";
    private static final int PORT = 22;
    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_OUTPUT_BYTES = 32768;

    private PradipwebSshClient() { }

    public static String run(byte[] privateKey, String fingerprint, Check check) throws Exception {
        if (privateKey == null || privateKey.length == 0)
            throw new IllegalArgumentException("No private key bytes were loaded. Re-import the SSH key.");
        if (fingerprint == null || fingerprint.trim().isEmpty())
            throw new IllegalArgumentException("No trusted server fingerprint is saved.");

        // Android versions may not expose Ed25519 to JSch's signature checks.
        // Register the bundled provider before SSH key-exchange negotiation.
        synchronized (PradipwebSshClient.class) {
            Security.removeProvider("BC");
            Class<?> providerClass = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider");
            java.security.Provider provider = (java.security.Provider) providerClass.getDeclaredConstructor().newInstance();
            Security.insertProviderAt(provider, 1);
        }

        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channel = null;
        try {
            jsch.setHostKeyRepository(new PinnedHostKeyRepository(HOST, fingerprint));
            try {
                jsch.addIdentity("mobile-operations-key", privateKey, null, null);
            } catch (JSchException keyError) {
                throw new IllegalStateException("SSH private key could not be parsed. Use the unencrypted PEM/OpenSSH private key that matches the ubuntu account's authorized key. Details: " + safeMessage(keyError), keyError);
            }

            session = jsch.getSession(USER, HOST, PORT);
            Properties config = new Properties();
            config.setProperty("StrictHostKeyChecking", "yes");
            config.setProperty("PreferredAuthentications", "publickey");
            config.setProperty("PubkeyAcceptedAlgorithms", "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256,ssh-rsa");
            // Pin the negotiated server host-key algorithm to the independently verified ED25519 key.
            config.setProperty("server_host_key", "ssh-ed25519");
            session.setConfig(config);
            session.setTimeout(TIMEOUT_MS);
            try {
                session.connect(TIMEOUT_MS);
            } catch (JSchException connectionError) {
                throw new IllegalStateException(explainSshException(connectionError), connectionError);
            }

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(commandFor(check));
            channel.setInputStream(null);
            InputStream stdout = channel.getInputStream();
            InputStream stderr = channel.getExtInputStream();
            channel.connect(TIMEOUT_MS);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            long deadline = System.currentTimeMillis() + TIMEOUT_MS;
            while (true) {
                drain(stdout, out, buffer);
                drain(stderr, err, buffer);
                if (channel.isClosed()) {
                    drain(stdout, out, buffer);
                    drain(stderr, err, buffer);
                    break;
                }
                if (System.currentTimeMillis() > deadline)
                    throw new java.net.SocketTimeoutException("SSH session connected, but the remote diagnostic command timed out.");
                Thread.sleep(60);
            }
            int exit = channel.getExitStatus();
            if (exit != 0) {
                String detail = err.toString("UTF-8").trim();
                if (detail.length() > 500) detail = detail.substring(0, 500);
                throw new IllegalStateException("SSH login succeeded, but the read-only command failed (exit " + exit + "). " +
                    (detail.isEmpty() ? "Check that the ubuntu account can run the requested command." : "Server response: " + detail));
            }
            return out.toString("UTF-8").trim();
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
            try { jsch.removeAllIdentity(); } catch (Exception ignored) { }
        }
    }

    private static void drain(InputStream stream, ByteArrayOutputStream target, byte[] buffer) throws Exception {
        while (stream != null && stream.available() > 0) {
            int count = stream.read(buffer);
            if (count < 0) break;
            if (target.size() + count > MAX_OUTPUT_BYTES)
                throw new IllegalStateException("Server response exceeded the 32 KB safety limit.");
            target.write(buffer, 0, count);
        }
    }

    public static String explainFailure(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = safeMessage(error);
        String lower = message.toLowerCase();
        if (lower.contains("fingerprint") || lower.contains("hostkey") || lower.contains("host key"))
            return "SERVER IDENTITY CHECK FAILED. Strict verification remains enabled. " + PinnedHostKeyRepository.getLastCheckDiagnostic() + "\\nSSH details: " + message;
        if (lower.contains("auth fail") || lower.contains("authentication") || lower.contains("publickey"))
            return "SSH AUTHENTICATION FAILED. The server was reached, but the key was not accepted for user ubuntu. Confirm that this exact public key is in /home/ubuntu/.ssh/authorized_keys and that the selected private key is its matching pair.\\nDetails: " + message;
        if (lower.contains("private key") || lower.contains("invalid privatekey") || lower.contains("invalid key"))
            return "PRIVATE KEY PROBLEM. Re-import the matching, unencrypted PEM/OpenSSH private key. Encrypted keys requiring a passphrase are not supported by this screen yet.\\nDetails: " + message;
        if (lower.contains("timeout") || lower.contains("timed out"))
            return "NETWORK / SSH TIMEOUT. Check internet access, VPN/firewall, server availability, and that TCP port 22 is reachable.\\nDetails: " + message;
        if (lower.contains("refused"))
            return "SSH PORT REFUSED THE CONNECTION. The server is reachable but port 22 is not accepting connections, or a firewall is rejecting it.\\nDetails: " + message;
        if (lower.contains("unresolved") || lower.contains("unknownhost") || lower.contains("no address"))
            return "HOST LOOKUP FAILED. Check the server address and network connection.\\nDetails: " + message;
        if (root instanceof java.security.GeneralSecurityException)
            return "LOCAL KEY DECRYPTION FAILED. Android Keystore could not decrypt the stored key. Re-import the private key.\\nDetails: " + message;
        return "CONNECTION CHECK FAILED.\\nDetails: " + message;
    }

    private static String explainSshException(JSchException error) {
        String m = safeMessage(error);
        String l = m.toLowerCase();
        if (l.contains("reject") || l.contains("hostkey") || l.contains("host key"))
            return "SERVER IDENTITY CHECK FAILED. Strict verification remains enabled. " + PinnedHostKeyRepository.getLastCheckDiagnostic() + " SSH details: " + m;
        if (l.contains("auth fail"))
            return "SSH authentication failed for ubuntu. The private key may not match /home/ubuntu/.ssh/authorized_keys. Details: " + m;
        if (l.contains("timeout") || l.contains("socket"))
            return "Could not establish SSH transport to " + HOST + ":22. Check network access, firewall, server status, and SSH port. Details: " + m;
        return "SSH transport error to " + HOST + ":22. Details: " + m;
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (message == null || message.trim().isEmpty()) return error == null ? "Unknown error" : error.getClass().getSimpleName();
        return message.length() > 700 ? message.substring(0, 700) : message;
    }

    private static String commandFor(Check check) {
        if (check == null) throw new IllegalArgumentException("Select a server check.");
        switch (check) {
            case HOSTNAME: return "hostname";
            case DOCKER_STATUS: return "docker ps --format '{{.Names}}|{{.Status}}|{{.State}}'";
            case SYSTEM_OVERVIEW:
                return "printf 'HOST|' ; hostname; " +
                    "printf 'UPTIME|' ; (uptime -p 2>/dev/null || cat /proc/uptime); " +
                    "printf 'LOAD|' ; cut -d ' ' -f1-3 /proc/loadavg; " +
                    "TOTAL=$(awk '/MemTotal:/ {print $2}' /proc/meminfo); " +
                    "AVAILABLE=$(awk '/MemAvailable:/ {print $2}' /proc/meminfo); " +
                    "USED=$((TOTAL-AVAILABLE)); printf 'MEMORY|%s/%s MiB\\n' \"$((USED/1024))\" \"$((TOTAL/1024))\"; " +
                    "df -hP / | tail -1 | sed 's/^/DISK|/'; " +
                    "docker ps --format '{{.Names}}|{{.Status}}' | sed 's/^/CONTAINER|/'; " +
                    "printf 'FRONTEND_HTTP|' ; (curl -sS -o /dev/null -w '%{http_code}' --max-time 4 http://127.0.0.1:5173/ 2>/dev/null || echo 000); printf '\\n'; " +
                    "printf 'BACKEND_HTTP|' ; (curl -sS -o /dev/null -w '%{http_code}' --max-time 4 http://127.0.0.1:8000/openapi.json 2>/dev/null || echo 000); printf '\\n'";
            default: throw new IllegalArgumentException("Unsupported read-only server check.");
        }
    }
}