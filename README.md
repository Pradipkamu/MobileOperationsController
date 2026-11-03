# Mobile Operations Controller

Native Android application for secure, approval-controlled operations monitoring.

## Current status

- **v0.2 dashboard UI:** native Android screen for Pradipweb connection, application health, Docker services, recent logs, and server details.
- **Important:** the current screen is a UI prototype only. It will continue to show **Not connected** because SSH/network monitoring has not been implemented yet. It does not currently test the server or retrieve live health, Docker status, or logs.
- Next milestone: implement SSH using encrypted Android Keystore-backed credential storage, strict host-key verification, and fixed allowlisted read-only commands.

## Get the latest development build

Run in Termux:

```bash
cd ~/MobileOperationsController
git fetch origin
git switch feature/pradipweb-monitor-dashboard 2>/dev/null || git switch --track origin/feature/pradipweb-monitor-dashboard
git pull --ff-only origin feature/pradipweb-monitor-dashboard
```

## Build and sign the APK in Termux

The following commands build v0.2 using the currently verified Termux toolchain. Run them from the repository root:

```bash
source ~/.bashrc
export JAVA_HOME="$PREFIX/lib/jvm/java-21-openjdk"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/lib/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

mkdir -p build/classes build/dex
rm -rf build/classes/* build/dex/*

javac -source 8 -target 8 \
  -bootclasspath "$ANDROID_HOME/platforms/android-35/android.jar" \
  -d build/classes \
  app/src/main/java/com/mobileops/controller/MainActivity.java

d8 --lib "$ANDROID_HOME/platforms/android-35/android.jar" \
  --output build/dex \
  build/classes/com/mobileops/controller/MainActivity.class

aapt2 link -o build/unsigned.apk \
  --manifest app/src/main/AndroidManifest.xml \
  -I "$ANDROID_HOME/platforms/android-28/android.jar" \
  --min-sdk-version 23 --target-sdk-version 35 \
  --version-code 2 --version-name 0.2

cp build/unsigned.apk build/controller.apk
cp build/dex/classes.dex build/classes.dex
(cd build && zip -q -u controller.apk classes.dex && rm classes.dex)

zipalign -f -p 4 build/controller.apk build/controller-aligned.apk
zipalign -c -v 4 build/controller-aligned.apk

apksigner sign \
  --ks "$HOME/.android/mobile-operations-controller.jks" \
  --ks-key-alias mobilecontroller \
  --out build/controller-signed.apk \
  build/controller-aligned.apk

apksigner verify --verbose build/controller-signed.apk
```

Copy the signed APK to Downloads:

```bash
cp build/controller-signed.apk ~/storage/downloads/
```

Then open **My Files → Downloads → controller-signed.apk** and install it. Do not run the APK as a Termux command.

The signing keystore must remain on the phone and must never be committed to GitHub. The current Termux AAPT2 package is too old for Android 35 resources, so this prototype uses the Android 28 framework for resource linking while compiling Java against Android 35.

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
- `docs/BUILD_TERMUX.md`: detailed manual APK build instructions.
