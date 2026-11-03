#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

REPO_DIR="$HOME/MobileOperationsController"
BRANCH="feature/termux-one-command-apk-builder"
APK_NAME="controller-v07-signed.apk"
DOWNLOAD_DIR="$HOME/storage/downloads"
KEYSTORE="$HOME/.android/mobile-operations-controller.jks"
KEY_ALIAS="mobilecontroller"
JSCH_VERSION="2.28.8"
JSCH_JAR="$REPO_DIR/libs/jsch-$JSCH_VERSION.jar"
BC_VERSION="1.86"
BC_JAR="$REPO_DIR/libs/bcprov-jdk18on-$BC_VERSION.jar"
ANDROID_PLATFORM="$HOME/lib/android-sdk/platforms/android-35/android.jar"
ANDROID_LINK_PLATFORM="$HOME/lib/android-sdk/platforms/android-28/android.jar"
BUILD_TOOLS="$HOME/lib/android-sdk/build-tools/35.0.0"
MARKER="SSH-DIAG-20261103-B4"

fail() { printf '\nERROR: %s\n' "$*" >&2; exit 1; }
step() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

[[ -d "$REPO_DIR/.git" ]] || fail "Repository not found at $REPO_DIR"
cd "$REPO_DIR"

CURRENT_BRANCH="$(git branch --show-current)"
[[ "$CURRENT_BRANCH" == "$BRANCH" ]] || fail "You are on '$CURRENT_BRANCH'. Run the one-time setup: git fetch origin && git switch --track origin/$BRANCH"

# Keep user edits safe. Generated build output and local JARs are ignored by Git.
if ! git diff --quiet || ! git diff --cached --quiet; then
  fail "Tracked source changes are present. Commit or save them before pulling; nothing was changed."
fi

step "Pull latest committed source from GitHub"
git fetch origin "$BRANCH"
git pull --ff-only origin "$BRANCH"

# Load the verified Termux Java / Android SDK environment.
export JAVA_HOME="${JAVA_HOME:-$PREFIX/lib/jvm/java-21-openjdk}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/lib/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$PREFIX/bin:$BUILD_TOOLS:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

[[ -x "$JAVA_HOME/bin/javac" ]] || fail "Java 21 not found at $JAVA_HOME"
[[ -f "$ANDROID_PLATFORM" ]] || fail "Android API 35 platform not found: $ANDROID_PLATFORM"
[[ -f "$ANDROID_LINK_PLATFORM" ]] || fail "Android API 28 link platform not found: $ANDROID_LINK_PLATFORM"
[[ -x "$BUILD_TOOLS/d8" ]] || fail "D8 not found at $BUILD_TOOLS/d8"
[[ -f "$KEYSTORE" ]] || fail "Signing keystore not found: $KEYSTORE"
[[ -d "$HOME/storage" ]] || fail "Termux shared storage is not configured. Run: termux-setup-storage"
[[ -d "$DOWNLOAD_DIR" ]] || mkdir -p "$DOWNLOAD_DIR"

for cmd in git curl aapt2 zip zipalign apksigner unzip strings file; do
  command -v "$cmd" >/dev/null 2>&1 || fail "Required command '$cmd' is missing. Install it in Termux first."
done

# Use Termux's ARM64 zipalign; the Android SDK copy in this setup is x86-64.
ZIPALIGN_PATH="$(command -v zipalign)"
case "$(file "$ZIPALIGN_PATH" 2>/dev/null || true)" in
  *x86-64*) fail "zipalign resolves to an x86-64 binary. Put Termux's $PREFIX/bin before Android SDK build-tools in PATH." ;;
esac

step "Prepare local build dependency"
mkdir -p "$REPO_DIR/libs"
if [[ ! -s "$JSCH_JAR" ]]; then
  curl -fL --retry 3 "https://repo.maven.apache.org/maven2/com/github/mwiede/jsch/$JSCH_VERSION/jsch-$JSCH_VERSION.jar" -o "$JSCH_JAR.tmp"
  mv -f "$JSCH_JAR.tmp" "$JSCH_JAR"
fi
[[ -s "$JSCH_JAR" ]] || fail "JSch dependency download failed"
if [[ ! -s "$BC_JAR" ]]; then
  curl -fL --retry 3 "https://repo.maven.apache.org/maven2/org/bouncycastle/bcprov-jdk18on/$BC_VERSION/bcprov-jdk18on-$BC_VERSION.jar" -o "$BC_JAR.tmp"
  mv -f "$BC_JAR.tmp" "$BC_JAR"
fi
[[ -s "$BC_JAR" ]] || fail "Bouncy Castle dependency download failed"
if ! "$JAVA_HOME/bin/jar" tf "$BC_JAR" | grep -F "org/bouncycastle/jce/provider/BouncyCastleProvider.class" >/dev/null; then
  rm -f "$BC_JAR"
  curl -fL --retry 3 "https://repo.maven.apache.org/maven2/org/bouncycastle/bcprov-jdk18on/$BC_VERSION/bcprov-jdk18on-$BC_VERSION.jar" -o "$BC_JAR.tmp"
  mv -f "$BC_JAR.tmp" "$BC_JAR"
  "$JAVA_HOME/bin/jar" tf "$BC_JAR" | grep -F "org/bouncycastle/jce/provider/BouncyCastleProvider.class" >/dev/null || fail "Downloaded Bouncy Castle JAR does not contain its provider class"
fi

WORK="$REPO_DIR/build/apk-work"
rm -rf "$WORK"
mkdir -p "$WORK/classes" "$WORK/dex"

step "Compile application source"
javac -source 8 -target 8 \
  -classpath "$ANDROID_PLATFORM:$JSCH_JAR:$BC_JAR" \
  -d "$WORK/classes" \
  app/src/main/java/com/mobileops/controller/MainActivity.java \
  app/src/main/java/com/mobileops/controller/PinnedHostKeyRepository.java \
  app/src/main/java/com/mobileops/controller/PradipwebSshClient.java

step "Generate fresh DEX"
"$BUILD_TOOLS/d8" \
  --lib "$ANDROID_PLATFORM" \
  --output "$WORK/dex" \
  "$WORK"/classes/com/mobileops/controller/*.class \
  "$JSCH_JAR" \
  "$BC_JAR"
[[ -s "$WORK/dex/classes.dex" ]] || fail "D8 did not produce classes.dex"

if ! grep -aF "$MARKER" "$WORK/dex/classes.dex" >/dev/null; then
  fail "Fresh DEX is missing expected build marker $MARKER. Refusing to package."
fi

step "Package a fresh APK"
aapt2 link \
  -o "$WORK/unsigned.apk" \
  --manifest app/src/main/AndroidManifest.xml \
  -I "$ANDROID_LINK_PLATFORM" \
  --min-sdk-version 23 \
  --target-sdk-version 35 \
  --version-code 7 \
  --version-name 0.7

cp "$WORK/unsigned.apk" "$WORK/controller.apk"
cp "$WORK/dex/classes.dex" "$WORK/classes.dex"
(cd "$WORK" && zip -q controller.apk classes.dex)
for EXTRA_DEX in "$WORK"/dex/classes[2-9].dex "$WORK"/dex/classes[1-9][0-9].dex; do
  [[ -f "$EXTRA_DEX" ]] || continue
  cp "$EXTRA_DEX" "$WORK/$(basename "$EXTRA_DEX")"
  (cd "$WORK" && zip -q controller.apk "$(basename "$EXTRA_DEX")")
done
[[ -s "$WORK/controller.apk" ]] || fail "APK packaging failed"

step "Align APK"
zipalign -f -p 4 "$WORK/controller.apk" "$WORK/controller-aligned.apk"
zipalign -c -v 4 "$WORK/controller-aligned.apk"

step "Sign APK (enter keystore password when prompted)"
apksigner sign \
  --ks "$KEYSTORE" \
  --ks-key-alias "$KEY_ALIAS" \
  --out "$WORK/$APK_NAME" \
  "$WORK/controller-aligned.apk"

step "Verify signature and packaged build"
apksigner verify --verbose "$WORK/$APK_NAME"
unzip -p "$WORK/$APK_NAME" classes.dex | grep -aF "$MARKER" >/dev/null || fail "Signed APK is missing expected build marker"

# Stage beside the destination, then atomically replace the old APK only after all checks pass.
TEMP_DEST="$DOWNLOAD_DIR/.$APK_NAME.new"
cp "$WORK/$APK_NAME" "$TEMP_DEST"
cmp -s "$WORK/$APK_NAME" "$TEMP_DEST" || fail "Download copy checksum comparison failed"
mv -f "$TEMP_DEST" "$DOWNLOAD_DIR/$APK_NAME"

step "Build completed"
printf 'APK: %s\n' "$DOWNLOAD_DIR/$APK_NAME"
ls -lh "$DOWNLOAD_DIR/$APK_NAME"
printf '\nThe previous APK was replaced only after the new APK passed alignment and signature verification.\n'
