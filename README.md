# Mobile Operations Controller

Native Android application for secure, approval-controlled operations monitoring.

## Current status

- **v0.2 dashboard UI:** native Android screen for Pradipweb connection, application health, Docker services, recent logs, and server details.
- The current dashboard is a presentation layer. It does **not** yet connect to the server or retrieve live health/log data.
- Next milestone: implement SSH using encrypted Android Keystore-backed credential storage, strict host-key verification, and allowlisted read-only commands.

## Server target

- Host: `137.23.40.188`
- SSH user: `ubuntu`
- Expected containers:
  - `productionreviewwebapp-frontend-1`
  - `productionreviewwebapp-backend-1`
  - `productionreviewwebapp-db-1`

## Security requirements

- Never commit private SSH keys, keystores, passwords, tokens, or signing configuration.
- Keep the Android signing keystore locally on the device.
- Verify the server host key before accepting an SSH connection.
- Do not provide arbitrary shell execution. Use fixed, allowlisted read-only commands.
- Do not implement server write operations without a separate approval workflow.
- Do not expose server services publicly as part of this app.

## Project structure

- `app/src/main`: Android manifest and Java activity.
- `docs/BUILD_TERMUX.md`: current manual APK build instructions.
