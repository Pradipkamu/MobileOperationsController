# Mobile Operations Controller

Native Android application for secure, approval-controlled operations monitoring.

## Current status

- Native Android dashboard and SSH setup foundation.
- Imported PEM private keys are encrypted at rest with Android Keystore (AES-GCM).
- A strict JSch-compatible host-key repository has been added. It compares the raw SSH host key's SHA-256 digest with the independently supplied OpenSSH fingerprint and fails closed on a host or fingerprint mismatch.
- **SSH transport, key authentication and live monitoring are not yet integrated.** The app must not display Connected until the host pin is checked during a real SSH handshake and public-key authentication succeeds.

## Pradipweb target

- Host: `137.23.40.188`
- SSH user: `ubuntu`
- Port: `22`
- Access policy: read-only

## Security requirements

- The private SSH key is selected through Android's document picker and encrypted at rest with an AES-GCM key held by Android Keystore.
- Never commit private SSH keys, keystores, passwords, tokens, or signing configuration.
- The host fingerprint must be obtained independently from the server console or administrator. Never trust an unverified first connection.
- SSH host-key verification and public-key authentication are required before reporting Connected.
- Use fixed allowlisted read-only commands only. No arbitrary shell execution.
- Do not expose Pradipweb's internal ports publicly.
- Server write operations are out of scope and require a separate explicit approval design.

## Remaining implementation steps

1. Integrate the maintained JSch SSH transport and connect it to the strict pinned-host verifier.
2. Decrypt the saved key only in memory and perform public-key authentication.
3. Add fixed read-only checks for Docker services, backend/frontend health and recent logs.
4. Add timeouts, cancellation, sanitized errors and connection-state tests.
5. Build and verify the APK on the target Android device.

## Project structure

See `app/src/main` for Android source and `docs/BUILD_TERMUX.md` for the Termux build process.
