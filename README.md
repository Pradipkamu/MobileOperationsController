# Mobile Operations Controller

Native Android application for secure, approval-controlled operations monitoring.

## Current status

- v0.1: Native Android Java dashboard prototype.
- Current screen is a static UI; server connectivity and SSH monitoring are not implemented yet.
- Initial development milestone: secure read-only monitoring of Pradipweb.

## Security

- Never commit private SSH keys, keystores, passwords, tokens, or signing configuration.
- Keep Android signing keystore locally on the device.
- Server write operations require explicit approval and are out of scope for the initial read-only milestone.

## Project structure

See `app/src/main` for the Android manifest and Java activity.
