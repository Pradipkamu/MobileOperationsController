# Build APK in Termux

The app is native Android Java and uses the maintained JSch SSH2 library for SSH transport.

## Environment

```bash
source ~/.bashrc
export JAVA_HOME="$PREFIX/lib/jvm/java-21-openjdk"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/lib/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

## Download the SSH library

JSch and Bouncy Castle are runtime dependencies. Bouncy Castle supplies the Ed25519 fallback implementation used by JSch when the runtime lacks the required Java-native EdDSA implementation. Without it, JSch may fail its host-signature capability check with `There are not any available sig algorithm.` Download both locally; do not commit either JAR into the repository.

```bash
mkdir -p libs
curl -fL --retry 3 \
  https://repo.maven.apache.org/maven2/com/github/mwiede/jsch/2.28.8/jsch-2.28.8.jar \
  -o libs/jsch-2.28.8.jar

curl -fL --retry 3 \\
  https://repo.maven.apache.org/maven2/org/bouncycastle/bcprov-jdk18on/1.86/bcprov-jdk18on-1.86.jar \\
  -o libs/bcprov-jdk18on-1.86.jar
```

## Compile and package

Run from the repository root:

```bash
mkdir -p build/classes build/dex
rm -rf build/classes/* build/dex/*
javac -source 8 -target 8 \
  -bootclasspath "$ANDROID_HOME/platforms/android-35/android.jar" \
  -classpath "libs/jsch-2.28.8.jar:libs/bcprov-jdk18on-1.86.jar" \
  -d build/classes \
  app/src/main/java/com/mobileops/controller/MainActivity.java \
  app/src/main/java/com/mobileops/controller/PinnedHostKeyRepository.java \
  app/src/main/java/com/mobileops/controller/PradipwebSshClient.java

d8 --lib "$ANDROID_HOME/platforms/android-35/android.jar" \
  --output build/dex \
  build/classes/com/mobileops/controller/*.class \
  libs/jsch-2.28.8.jar \\
  libs/bcprov-jdk18on-1.86.jar

aapt2 link -o build/unsigned.apk \
  --manifest app/src/main/AndroidManifest.xml \
  -I "$ANDROID_HOME/platforms/android-28/android.jar" \
  --min-sdk-version 23 --target-sdk-version 35 \
  --version-code 4 --version-name 0.4

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

## SSH safety and current scope

- The app encrypts the imported PEM key at rest using Android Keystore AES-GCM.
- The SSH client accepts only the configured Pradipweb host and requires the saved SHA256 fingerprint to match the raw SSH host-key digest. Mismatches fail closed.
- The connection screen reports Connected only after SSH handshake verification, public-key authentication and the hostname/Docker read-only checks complete.
- The only remote commands exposed by the current client are fixed hostname and Docker status diagnostics. There is no arbitrary command input and no server write operation.
- No SSH private key, signing keystore or password belongs in GitHub.

The first live connection still requires the user to enter a fingerprint obtained independently from the server console/admin. A successful build and a successful real server connection must be verified separately on the phone.
