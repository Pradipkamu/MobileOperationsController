# Build APK in Termux (prototype v0.1)

This repository currently preserves the working Java prototype and its manually verified Termux build flow. It does not yet use the Android Gradle Plugin.

## 1. Set Android SDK environment

```bash
export JAVA_HOME="$PREFIX/lib/jvm/java-21-openjdk"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/lib/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

## 2. Compile Java and generate DEX

Run from the repository root:

```bash
mkdir -p build/classes build/dex
javac -source 8 -target 8 \
  -bootclasspath "$ANDROID_HOME/platforms/android-35/android.jar" \
  -d build/classes \
  app/src/main/java/com/mobileops/controller/MainActivity.java

d8 --lib "$ANDROID_HOME/platforms/android-35/android.jar" \
  --output build/dex \
  build/classes/com/mobileops/controller/MainActivity.class
```

## 3. Package APK

The current Termux AAPT2 package is too old for the Android 35 resource table. The working prototype uses the Android 28 framework for resource linking while compiling Java against Android 35.

```bash
aapt2 link -o build/unsigned.apk \
  --manifest app/src/main/AndroidManifest.xml \
  -I "$ANDROID_HOME/platforms/android-28/android.jar" \
  --min-sdk-version 23 --target-sdk-version 35 \
  --version-code 1 --version-name 0.1

cp build/unsigned.apk build/controller.apk
cp build/dex/classes.dex build/classes.dex
(cd build && zip -q controller.apk classes.dex && rm classes.dex)
zipalign -f -p 4 build/controller.apk build/controller-aligned.apk
zipalign -c -v 4 build/controller-aligned.apk
```

## 4. Sign locally

The signing keystore must remain on the phone and must never be committed.

```bash
apksigner sign \
  --ks "$HOME/.android/mobile-operations-controller.jks" \
  --ks-key-alias mobilecontroller \
  --out build/controller-signed.apk \
  build/controller-aligned.apk

apksigner verify --verbose build/controller-signed.apk
```

Install `build/controller-signed.apk` on the phone. Keep the keystore and its passwords private.

## Planned improvement

Replace this manual packaging flow with a reproducible Gradle Android build once a compatible Android Gradle Plugin and AAPT2 setup is established for Termux.
