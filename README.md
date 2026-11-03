# Mobile Operations Controller

Native Android application for secure, approval-controlled operations monitoring.

## Current implementation

- Native Android screen for Pradipweb connection setup.
- Imported PEM private keys are encrypted at rest using Android Keystore AES-GCM.
- JSch SSH2 transport is integrated in the source.
- SSH host verification uses a strict SHA256 pin for the configured host. Unknown or mismatched host keys are rejected.
- The app has a read-only connection test for hostname and Docker container status. It has no arbitrary shell-command field and no server write operations.

**Build and live connection status are not yet verified on the target phone/server.** The UI must only show Connected after the SSH handshake passes the pinned host-key check, public-key authentication succeeds and both read-only checks return successfully.

## Pradipweb target

- Host: `137.23.40.188`
- SSH user: `ubuntu`
- Port: `22`
- Access policy: read-only

## First connection setup

1. Obtain the SSH host's SHA256 fingerprint independently from the server console or administrator.
2. Import the existing PEM private key using the Android document picker.
3. Enter and save the independently verified fingerprint.
4. Build and install the APK using [Termux build instructions](docs/BUILD_TERMUX.md).
5. Run **TEST SSH & READ SERVER STATUS**. A mismatched fingerprint or failed key authentication must fail closed.

Never accept a fingerprint merely because it is displayed by an untrusted first connection.

## Security requirements

- Private key material is encrypted at rest with an AES-GCM key held by Android Keystore and decrypted only in memory for the connection attempt.
- Never commit private SSH keys, keystores, passwords, tokens, or signing configuration.
- Use fixed allowlisted read-only commands only. No arbitrary shell execution.
- Do not expose Pradipweb's internal ports publicly.
- Server write operations are out of scope and require a separate explicit approval design.

## Project structure

See `app/src/main` for Android source and `docs/BUILD_TERMUX.md` for Termux build and signing steps.
