# Termux Build and SSH Troubleshooting Record

**Last verified:** 2026-11-03  
**Target:** Android 13 / ARM64 device, Termux, Pradipweb SSH  
**App version:** 0.4

This document records the build sequence that successfully produced a signed APK containing the current diagnostic UI, and the latest live SSH test result. Build success and SSH connection success are separate checks.

## Verified environment

- Java: OpenJDK 21 in Termux.
- Android SDK: `$HOME/lib/android-sdk`.
- Android compile platform: API 35.
- AAPT2 link platform workaround: API 28.
- Minimum SDK: 23; target SDK: 35.
- JSch dependency used locally: mwiede JSch 2.28.8.
- Device architecture: ARM64.

Restore the environment after starting a new Termux session:

```bash
export JAVA_HOME="$PREFIX/lib/jvm/java-21-openjdk"
export ANDROID_HOME="$HOME/lib/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$PREFIX/bin:$ANDROID_HOME/build-tools/35.0.0:$PATH"
```

**Important:** Put `$PREFIX/bin` before the Android SDK build-tools directory. The SDK's `zipalign` binary in this setup is x86-64 and cannot run on ARM64. Termux's `$PREFIX/bin/zipalign` is the working ARM64 binary. Confirm with:

```bash
command -v zipalign
file "$(command -v zipalign)"
```

Expected architecture: ARM64 / AArch64.

## Local-only inputs

The following files must remain on the phone and must never be committed:

- `$HOME/.android/mobile-operations-controller.jks` — APK signing keystore.
- `~/.ssh/pradipweb.key` — SSH private key.
- Any passwords, decrypted key material, or exported credentials.

The JSch JAR is also a local build dependency in this workflow; download it into `libs/` if it is not present:

```bash
mkdir -p libs
curl -fL --retry 3 \
  https://repo.maven.apache.org/maven2/com/github/mwiede/jsch/2.28.8/jsch-2.28.8.jar \
  -o libs/jsch-2.28.8.jar
```

## Verified clean compile and DEX generation

Run from the repository root:

```bash
rm -rf build/classes-v04 build/dex-v04
mkdir -p build/classes-v04 build/dex-v04

javac -source 8 -target 8 \
  -classpath "$ANDROID_HOME/platforms/android-35/android.jar:libs/jsch-2.28.8.jar" \
  -d build/classes-v04 \
  app/src/main/java/com/mobileops/controller/MainActivity.java \
  app/src/main/java/com/mobileops/controller/PinnedHostKeyRepository.java \
  app/src/main/java/com/mobileops/controller/PradipwebSshClient.java

"$ANDROID_HOME/build-tools/35.0.0/d8" \
  --lib "$ANDROID_HOME/platforms/android-35/android.jar" \
  --output build/dex-v04 \
  build/classes-v04/com/mobileops/controller/*.class \
  libs/jsch-2.28.8.jar
```

The Java 8 source/target and deprecated-API messages seen during compilation were warnings, not build failures.

## Package the APK (important fresh-Dex procedure)

AAPT2 in this Termux setup was linked against Android 28 resources as a workaround, while Java compilation uses Android 35:

```bash
aapt2 link \
  -o build/unsigned-v04.apk \
  --manifest app/src/main/AndroidManifest.xml \
  -I "$ANDROID_HOME/platforms/android-28/android.jar" \
  --min-sdk-version 23 \
  --target-sdk-version 35 \
  --version-code 4 \
  --version-name 0.4

rm -f build/controller-v04.apk
cp build/unsigned-v04.apk build/controller-v04.apk
cp build/dex-v04/classes.dex build/classes.dex
(cd build && zip -q controller-v04.apk classes.dex)
rm -f build/classes.dex
```

Do **not** use `zip -u` for this replacement step. It can leave the old `classes.dex` in place when the archive timestamp is newer. Confirm the newly packaged DEX before continuing:

```bash
unzip -p build/controller-v04.apk classes.dex | strings | grep "SSH-DIAG-20261103-B1"
```

Expected diagnostic build marker:

```text
BUILD: SSH-DIAG-20261103-B1
```

This marker was added below the app title in `MainActivity.java` to make the installed build identifiable.

## Align, sign and verify

Use Termux's ARM64 `zipalign`, not the x86-64 SDK binary:

```bash
zipalign -f -p 4 build/controller-v04.apk build/controller-v04-aligned.apk
zipalign -c -v 4 build/controller-v04-aligned.apk
```

The signing keystore is a JKS file at `$HOME/.android/mobile-operations-controller.jks`, alias `mobilecontroller`. It contains a `PrivateKeyEntry`. The user created one password for the keystore/key entry. Let Termux's `apksigner` prompt interactively; do not store the password in the repository or shell history:

```bash
apksigner sign \
  --ks "$HOME/.android/mobile-operations-controller.jks" \
  --ks-key-alias mobilecontroller \
  --out build/controller-v04-signed.apk \
  build/controller-v04-aligned.apk

apksigner verify --verbose build/controller-v04-signed.apk
unzip -p build/controller-v04-signed.apk classes.dex | strings | grep "SSH-DIAG-20261103-B1"

mkdir -p ~/storage/downloads
cp build/controller-v04-signed.apk ~/storage/downloads/
```

A successful signed-APK check in this session confirmed that the signed archive contains `BUILD: SSH-DIAG-20261103-B1`. The APK copied to Downloads was also checksum-identical to the project APK in a prior packaging attempt. Install over the existing app using the same signing keystore; do not uninstall unless Android explicitly requires it and the app data implications are understood.

For future sessions, the password environment variable approach is optional and session-only. If used, set it interactively with `read -s` and unset it when finished. Never persist it in `.bashrc`, project files, or GitHub.

## Verified Pradipweb identity and SSH tests

Target endpoint: `137.23.40.188:22`, SSH user `ubuntu`.

The server-console ED25519 host-key fingerprint was independently checked as:

```text
SHA256:Oi1uA8IIsD3//CbhUjbBZBZ/d6QTVmqgIpMkGtcZdnM
```

A Termux `ssh-keyscan` fingerprint check returned the same ED25519 fingerprint. Strict host-key verification remains enabled in the Android app; never bypass it or automatically trust a key received from an unverified connection.

The user's imported client identity is an RSA 2048-bit private key. The following normal Termux SSH test succeeded:

```bash
ssh-keygen -lf ~/.ssh/pradipweb.key
ssh -o BatchMode=yes -i ~/.ssh/pradipweb.key ubuntu@137.23.40.188 "echo SSH_AUTH_OK"
```

Observed output:

```text
2048 SHA256:7TznocjA6pOAoBaeJEUlqUyKfoEHV9AFGDNLPtZV5y0 no comment (RSA)
SSH_AUTH_OK
```

## Latest Android app connection result

The Android app initially reported a host-key mismatch. Diagnostic instrumentation was added in `PinnedHostKeyRepository.java` and surfaced through `PradipwebSshClient.java`. A fresh DEX was verified to contain the diagnostic strings, and the app's subsequent error changed to:

```text
There are not any available sig algorithm.
```

This indicates the connection progressed beyond the earlier host-key mismatch and is now failing during client public-key signature algorithm selection/authentication. The normal Termux RSA key login works, but Android JSch 2.28.8 has not yet completed RSA public-key authentication in the app.

The client currently configures the accepted public-key algorithm list in `PradipwebSshClient.java` and pins the server host-key negotiation to `ssh-ed25519`. The next engineering task is to inspect JSch 2.28.8 RSA-SHA2 signature implementation/provider compatibility and configure a supported signature algorithm without weakening host-key verification or changing server SSH configuration.

## Security and scope

- Private SSH key encryption at rest uses Android Keystore AES-GCM.
- Host-key pinning is strict and fail-closed.
- Public-key authentication only.
- Read-only connection checks only; no arbitrary shell commands or server write operations.
- Do not change Pradipweb SSH configuration or application services as part of client-side troubleshooting.
- Never commit SSH keys, signing keystores, passwords, or credentials.
