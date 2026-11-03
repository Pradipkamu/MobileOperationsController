# Mobile Operations Controller

Native Android application for secure, approval-controlled operations monitoring.

## Current status

- v0.1: Native Android dashboard prototype.
- SSH setup foundation: Android Keystore-backed encryption for an imported PEM private key and local storage of an independently verified OpenSSH SHA256 host fingerprint.
- The current SSH setup screen does **not yet authenticate to the server** or retrieve live health/log data. It must not display a successful connection until SSH host-key verification and key-based authentication are implemented and tested.

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

## Next implementation steps

1. Integrate and test an Android-compatible SSH transport library.
2. Enforce the saved SHA256 host fingerprint during the SSH handshake.
3. Authenticate using the decrypted key only in memory; never write plaintext key material to disk.
4. Add fixed read-only checks for Docker services, backend/frontend health and recent logs.
5. Add timeouts, cancellation, sanitized errors and connection-state tests.

## Project structure

See `app/src/main` for Android source and `docs/BUILD_TERMUX.md` for the Termux build process.
