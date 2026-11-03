# Build APK in Termux

The app currently uses native Android Java APIs and no third-party runtime library.

## Environment

```bash
source ~/.bashrc
export JAVA_HOME="$PREFIX/lib/jvm/java-21-openjdk"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/lib/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

## Compile and package

Run from the repository root:

```bash
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
  --version-code 3 --version-name 0.3

cp build/unsigned.apk build/controller.apk
cp build/dex/classes.dex build/classes.dex
(cd build && zip -q -u controller.apk classes.dex && rm classes.dex)
zipalign -f -p 4 build/controller.apk build/controller-aligned.apk
zipalign -c -v 4 build/controller-aligned.apk
```

The currently installed Termux AAPT2 may be too old for the Android 35 framework resource table. The workaround above links resources against Android 28 while compiling Java against Android 35. Check the output carefully; this is a temporary toolchain workaround.

## Sign locally

Keep the signing keystore only on the phone. Never commit it.

```bash
apksigner sign \
  --ks "$HOME/.android/mobile-operations-controller.jks" \
  --ks-key-alias mobilecontroller \
  --out build/controller-signed.apk \
  build/controller-aligned.apk

apksigner verify --verbose build/controller-signed.apk
mkdir -p ~/storage/downloads
cp build/controller-signed.apk ~/storage/downloads/
```

Install from Android My Files → Downloads → controller-signed.apk. Do not run the APK as a Termux shell command.

## Current feature status

This milestone adds Android Keystore AES-GCM storage for a user-selected PEM private key and local storage for a manually supplied SHA256 SSH host fingerprint. It does not yet implement SSH transport, handshake verification, authenticated connection, health checks, Docker status, or log retrieval. The app must not show Connected until those steps are implemented and verified.

The app requests INTERNET permission in preparation for the SSH transport. No private key or signing material belongs in the repository.
