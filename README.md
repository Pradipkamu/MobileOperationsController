# Mobile Operations Controller

Native Android application for secure, approval-controlled operations monitoring.

## Current implementation

- Native Android screen for Pradipweb connection setup.
- Imported PEM private keys are encrypted at rest using Android Keystore AES-GCM.
- JSch SSH2 transport is integrated in the source.
- SSH host verification uses a strict SHA256 pin for the configured host. Unknown or mismatched host keys are rejected.
- The app has a read-only connection test for hostname and Docker container status. It has no arbitrary shell-command field and no server write operations.

**Build status:** A signed version 0.4 APK was successfully produced in Termux on ARM64, and the packaged DEX was verified to contain the diagnostic build marker `SSH-DIAG-20261103-B1`.

**Live SSH status:** Strict server host-key verification now progresses past the earlier host-key mismatch, but Android JSch public-key authentication currently fails with `There are not any available sig algorithm.` Normal Termux SSH authentication using the same RSA 2048-bit client key succeeds. The Android RSA signature-algorithm compatibility issue remains open. The UI must only show Connected after host-key verification, public-key authentication and both read-only checks succeed.

See [the verified Termux build and SSH troubleshooting record](docs/TERMUX_BUILD_AND_SSH_STATUS_2026-11-03.md) for exact commands, toolchain workarounds and test results.

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


## One-command Termux build (recommended)

After the one-time branch setup below, future builds use a single command:

```bash
cd ~/MobileOperationsController && bash build_apk.sh
```

The script fetches and fast-forwards this branch, downloads the pinned JSch dependency if missing, compiles the Android Java source, creates a fresh DEX/APK, aligns it, signs it with the keystore kept on the phone, verifies the signature, and atomically replaces `~/storage/downloads/controller-v04-signed.apk`. It never deletes the previous Downloads APK until the new APK has passed signature verification.

### One-time setup on an existing Termux checkout

Run these commands once to switch the existing checkout to the maintained development branch:

```bash
cd ~/MobileOperationsController
git fetch origin
git switch --track origin/feature/termux-one-command-apk-builder
```

Do not reset or discard local changes. The branch includes the current SSH connection source and the visible diagnostic build marker.

### Local-only files

The signing keystore, SSH private key, build output, downloaded JAR dependencies and backup copies remain local and are excluded from Git. Never upload private keys, keystores, passwords or signing configuration.
