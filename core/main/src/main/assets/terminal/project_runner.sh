# project_runner.sh — project-aware run/build, executed inside the Ubuntu sandbox.
#
# Invoked by com.rk.runner.ProjectRunner. The project type is detected on the
# Kotlin side (ProjectTypeDetector) and passed in, so this script only has to
# check that the required toolchain is present and run/build the project from
# its own root directory.
#
# Args:
#   $1 = project type   (DetectedProjectType enum name: PYTHON, NODE, FABRIC_MOD,
#                         FORGE_MOD, GRADLE, ANDROID, SYNC, RUST, GO, WEB)
#   $2 = project dir     (absolute path; already the working directory)
#   $3 = entry file      (absolute path of the currently open file, optional)
#   $4 = gradle args     (extra flags for gradle builds, space-separated; optional)
#                         e.g. "--info --stacktrace --offline". Set per project in the
#                         IDE Configuration view (see com.rk.projects.GradleConfig).
#   $5 = build type      (debug|release; Android picks assembleDebug/assembleRelease)
#
# No `set -e`: we want to surface build/run errors to the user and keep the
# terminal open so the output (and any errors) stay visible.

source "$LOCAL/bin/utils"

export DEBIAN_FRONTEND=noninteractive

TYPE="${1:-UNKNOWN}"
PROJECT_DIR="${2:-$PWD}"
ENTRY="${3:-}"
GRADLE_ARGS="${4:-}"
BUILD_TYPE="${5:-debug}"

# Resolve the directory we can actually enter. Shared storage is reliably available at /sdcard
# inside the sandbox, while the canonical /storage/emulated/0 form sometimes isn't, so fall back
# to the /sdcard equivalent before giving up.
enter_dir() {
  cd "$1" 2>/dev/null && return 0
  case "$1" in
    /storage/emulated/0/*) cd "/sdcard/${1#/storage/emulated/0/}" 2>/dev/null && return 0 ;;
    /storage/self/primary/*) cd "/sdcard/${1#/storage/self/primary/}" 2>/dev/null && return 0 ;;
  esac
  return 1
}

if ! enter_dir "$PROJECT_DIR"; then
  error "Cannot enter project directory: $PROJECT_DIR"
  exit 1
fi
PROJECT_DIR="$PWD"

info "Project : $PROJECT_DIR"
info "Type    : $TYPE"
case "$TYPE" in
  FABRIC_MOD | FORGE_MOD | GRADLE | ANDROID | SYNC)
    info "Build   : $BUILD_TYPE"
    [ -n "$GRADLE_ARGS" ] && info "Gradle  : $GRADLE_ARGS"
    ;;
esac

# --- helpers ---------------------------------------------------------------

show_result() {
  local code="$1"
  if [ "$code" -eq 0 ]; then
    printf '\n\033[1;42m  DONE  \033[0m \033[1;32mFinished successfully (exit %s)\033[0m\n' "$code"
  else
    printf '\n\033[1;41m FAILED \033[0m \033[1;31mProcess exited with code %s\033[0m\n' "$code"
  fi
  return "$code"
}

# need <bin> <human-readable name>
need() {
  if ! command_exists "$1"; then
    error "Required tool '$1' ($2) is not installed in the sandbox."
    warn  "Open the Dependencies dialog (the download icon in the editor toolbar) and install it, then run again."
    exit 127
  fi
}

# Detect the Java version requested by the Gradle build files (8, 11, 17, 21) and ensure
# that exact OpenJDK is installed and exported as JAVA_HOME for this build.
ensure_project_jdk() {
  local req_jdk=""
  local files="app/build.gradle.kts build.gradle.kts app/build.gradle build.gradle"

  # 1. Check JavaLanguageVersion.of(NN) or jvmToolchain(NN)
  req_jdk=$(grep -hoE '(JavaLanguageVersion\.of|jvmToolchain)[[:space:]]*\([[:space:]]*[0-9]+' $files 2>/dev/null | grep -oE '[0-9]+' | head -1)
  # 2. Check options.release = NN
  if [ -z "$req_jdk" ]; then
    req_jdk=$(grep -hoE 'release[[:space:]]*=[[:space:]]*[0-9]+' $files 2>/dev/null | grep -oE '[0-9]+' | head -1)
  fi
  # 3. Check VERSION_1_8 / VERSION_NN / JVM_1_8 / JVM_NN
  if [ -z "$req_jdk" ]; then
    local ver_tok
    ver_tok=$(grep -hoE '(VERSION|JVM)_(1_8|[0-9]+)' $files 2>/dev/null | head -1)
    case "$ver_tok" in
      *1_8) req_jdk="8" ;;
      *) req_jdk=$(printf '%s' "$ver_tok" | grep -oE '[0-9]+' | head -1) ;;
    esac
  fi

  if [ -z "$req_jdk" ]; then
    req_jdk="21"
  fi

  local jvm_dir
  jvm_dir=$(ls -d /usr/lib/jvm/java-${req_jdk}-openjdk* 2>/dev/null | head -1)
  if [ -z "$jvm_dir" ] || [ ! -x "$jvm_dir/bin/javac" ]; then
    info "JDK $req_jdk required by project — installing openjdk-${req_jdk}-jdk ..."
    apt-get update -y && apt-get install -y "openjdk-${req_jdk}-jdk" || {
      warn "Could not install openjdk-${req_jdk}-jdk via apt; falling back to system Java."
    }
    jvm_dir=$(ls -d /usr/lib/jvm/java-${req_jdk}-openjdk* 2>/dev/null | head -1)
  fi

  if [ -n "$jvm_dir" ] && [ -x "$jvm_dir/bin/java" ]; then
    export JAVA_HOME="$jvm_dir"
    export PATH="$JAVA_HOME/bin:$PATH"
    info "Using JDK : $JAVA_HOME"
  fi
}

gradle_build() {
  ensure_project_jdk
  need java "JDK"
  if [ ! -f ./gradlew ]; then
    error "gradlew not found in the project root. This Gradle project is missing its wrapper."
    exit 1
  fi
  # Make the wrapper executable (shared storage / fresh clones often drop the +x bit).
  chmod +x ./gradlew 2>/dev/null
  info "Building with ./gradlew build $GRADLE_ARGS ..."
  # $GRADLE_ARGS is intentionally unquoted so multiple flags word-split into separate args.
  run_gradlew build $GRADLE_ARGS
  show_result $?
}

# Point Gradle at the Android SDK (installed via the Dependencies dialog into $HOME/android-sdk),
# and write/repair local.properties so the Android Gradle plugin can find it.
setup_android_sdk() {
  if [ -z "$ANDROID_HOME" ] && [ -d "$HOME/android-sdk" ]; then
    export ANDROID_HOME="$HOME/android-sdk"
  fi
  export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
  if [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ]; then
    if [ ! -f local.properties ]; then
      printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties 2>/dev/null || true
    else
      local cur_sdk
      cur_sdk=$(grep -E '^[[:space:]]*sdk\.dir[[:space:]]*=' local.properties 2>/dev/null | head -1 | cut -d'=' -f2- | tr -d '\r')
      if [ -z "$cur_sdk" ] || [ ! -d "$cur_sdk" ]; then
        grep -vE '^[[:space:]]*sdk\.dir[[:space:]]*=' local.properties > local.properties.tmp 2>/dev/null || true
        printf 'sdk.dir=%s\n' "$ANDROID_HOME" >> local.properties.tmp
        mv local.properties.tmp local.properties 2>/dev/null || true
      fi
    fi
  else
    warn "Android SDK not found. Open the Dependencies dialog (download icon) and install 'Android SDK', then run again."
  fi
}

# Run gradlew with the given args, honouring the noexec fallback.
run_gradlew() {
  chmod +x ./gradlew 2>/dev/null
  if [ -x ./gradlew ]; then
    ./gradlew "$@"
  else
    bash ./gradlew "$@"
  fi
}

# Make sure the Android SDK (and a matching JDK + ARM64 aapt2) are present before building.
ensure_android_sdk() {
  export ANDROID_HOME="$HOME/android-sdk"
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
  local proj="$PWD"
  local SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

  ensure_project_jdk

  # 1. Bootstrap the SDK (JDK + command-line tools + platform-tools + aapt) if it isn't installed yet.
  if [ ! -x "$SDKM" ] || [ ! -d "$ANDROID_HOME/platform-tools" ]; then
    info "Android SDK not found — downloading it now (this can take a while) ..."
    apt-get update -y && apt-get install -y wget curl unzip aapt openjdk-17-jdk openjdk-21-jdk || {
      error "Could not install prerequisites (wget/unzip/aapt/JDK)."
      cd "$proj" 2>/dev/null
      return 1
    }
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    cd "$ANDROID_HOME/cmdline-tools" || { cd "$proj" 2>/dev/null; return 1; }
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O clt.zip || {
      error "Failed to download Android command-line tools."
      cd "$proj" 2>/dev/null
      return 1
    }
    unzip -q -o clt.zip && rm -f clt.zip && rm -rf latest && mv cmdline-tools latest
    yes | "$SDKM" --sdk_root="$ANDROID_HOME" --licenses >/dev/null 2>&1 || true
    "$SDKM" --sdk_root="$ANDROID_HOME" "platform-tools" || {
      error "Android SDK platform-tools install failed."
      cd "$proj" 2>/dev/null
      return 1
    }
    cd "$proj" 2>/dev/null
  fi

  # Configure native aapt2 on non-x86_64 hosts (e.g. ARM64 Android devices) so AGP doesn't fail on x86_64 binaries.
  local ARCH
  ARCH=$(uname -m)
  if [ "$ARCH" != "x86_64" ]; then
    if ! command_exists aapt2; then
      apt-get update -y && apt-get install -y aapt >/dev/null 2>&1 || true
    fi
    if command_exists aapt2; then
      local AAPT2_BIN
      AAPT2_BIN=$(command -v aapt2)
      mkdir -p "$HOME/.gradle"
      if grep -q '^android\.aapt2FromMavenOverride=' "$HOME/.gradle/gradle.properties" 2>/dev/null; then
        sed -i "s|^android\.aapt2FromMavenOverride=.*|android.aapt2FromMavenOverride=$AAPT2_BIN|" "$HOME/.gradle/gradle.properties"
      else
        printf '\nandroid.aapt2FromMavenOverride=%s\n' "$AAPT2_BIN" >> "$HOME/.gradle/gradle.properties"
      fi
    fi
  fi

  [ -x "$SDKM" ] || return 0
  yes | "$SDKM" --sdk_root="$ANDROID_HOME" --licenses >/dev/null 2>&1 || true

  # 2. Install the EXACT platform the project targets (compileSdk from the build file).
  local CSDK
  CSDK=$(grep -hoE 'compileSdk(Version)?[[:space:]]*[=(]?[[:space:]]*[0-9]+' app/build.gradle.kts build.gradle.kts app/build.gradle build.gradle 2>/dev/null | grep -oE '[0-9]+' | head -1)
  if [ -z "$CSDK" ]; then
    CSDK=$("$SDKM" --sdk_root="$ANDROID_HOME" --list 2>/dev/null | grep -oE 'platforms;android-[0-9]+' | grep -oE '[0-9]+' | sort -n | tail -1)
  fi
  if [ -n "$CSDK" ] && [ ! -d "$ANDROID_HOME/platforms/android-$CSDK" ]; then
    info "Installing platform android-$CSDK ..."
    "$SDKM" --sdk_root="$ANDROID_HOME" "platforms;android-$CSDK" || warn "Could not install platform android-$CSDK; Gradle may fetch it during the build."
  fi

  # 3. Make sure at least one build-tools is present (newest available).
  if [ ! -d "$ANDROID_HOME/build-tools" ] || [ -z "$(ls -A "$ANDROID_HOME/build-tools" 2>/dev/null)" ]; then
    local BT
    BT=$("$SDKM" --sdk_root="$ANDROID_HOME" --list 2>/dev/null | grep -oE 'build-tools;[0-9.]+' | sort -V | tail -1)
    [ -z "$BT" ] && BT='build-tools;35.0.0'
    info "Installing $BT ..."
    "$SDKM" --sdk_root="$ANDROID_HOME" "$BT" || warn "Could not install $BT; Gradle may fetch it during the build."
  fi

  if [ "$ARCH" != "x86_64" ] && command_exists aapt2; then
    local AAPT2_BIN
    AAPT2_BIN=$(command -v aapt2)
    for bt in "$ANDROID_HOME/build-tools"/*/; do
      [ -d "$bt" ] && ln -sf "$AAPT2_BIN" "${bt}aapt2" 2>/dev/null || true
    done
  fi

  cd "$proj" 2>/dev/null
  return 0
}

# --- per-type dispatch -----------------------------------------------------

case "$TYPE" in
  PYTHON)
    need python3 "Python 3"
    target="$ENTRY"
    # Fall back to a conventional entry point if the open file isn't a .py file.
    if [ -z "$target" ] || [ ! -f "$target" ] || [ "${target##*.}" != "py" ]; then
      target=""
      for candidate in main.py app.py __main__.py run.py manage.py; do
        if [ -f "$candidate" ]; then target="$candidate"; break; fi
      done
    fi
    if [ -z "$target" ]; then
      error "No Python entry point found (looked for the open file, main.py, app.py, run.py, manage.py)."
      exit 1
    fi
    if [ -s requirements.txt ]; then
      info "Installing dependencies from requirements.txt ..."
      python3 -m pip install --break-system-packages -r requirements.txt 2>/dev/null \
        || python3 -m pip install -r requirements.txt \
        || warn "pip install failed; running anyway."
    fi
    info "Running: python3 $target"
    python3 "$target"
    show_result $?
    ;;

  NODE)
    need node "Node.js"
    if [ -f package.json ]; then
      need npm "npm"
      if [ ! -d node_modules ] && grep -qE '"(dependencies|devDependencies)"' package.json; then
        info "Installing npm dependencies ..."
        npm install || warn "npm install failed; running anyway."
      fi
      if grep -q '"start"' package.json; then
        info "Running: npm start"
        npm start
      elif [ -n "$ENTRY" ] && [ -f "$ENTRY" ] && [ "${ENTRY##*.}" = "js" ]; then
        info "Running: node $ENTRY"
        node "$ENTRY"
      else
        info "Running: node index.js"
        node index.js
      fi
    else
      info "Running: node ${ENTRY:-index.js}"
      node "${ENTRY:-index.js}"
    fi
    show_result $?
    ;;

  FABRIC_MOD | FORGE_MOD | GRADLE)
    gradle_build
    ;;

  ANDROID)
    ensure_android_sdk || { show_result 1; exit 1; }
    need java "JDK"
    setup_android_sdk
    if [ ! -f ./gradlew ]; then
      error "gradlew not found in the project root. This Android project is missing its wrapper."
      exit 1
    fi
    if [ "$BUILD_TYPE" = "release" ]; then
      ASSEMBLE_TASK="assembleRelease"
      OUT_DIR="release"
    else
      ASSEMBLE_TASK="assembleDebug"
      OUT_DIR="debug"
    fi
    info "Building APK with ./gradlew $ASSEMBLE_TASK $GRADLE_ARGS ..."
    # $GRADLE_ARGS is intentionally unquoted so multiple flags word-split into separate args.
    run_gradlew "$ASSEMBLE_TASK" $GRADLE_ARGS
    code=$?
    if [ "$code" -eq 0 ]; then
      apk="$(ls -t "app/build/outputs/apk/$OUT_DIR"/*.apk "build/outputs/apk/$OUT_DIR"/*.apk 2>/dev/null | head -n1)"
      if [ -n "$apk" ]; then
        apk_abs="$(cd "$(dirname "$apk")" 2>/dev/null && pwd)/$(basename "$apk")"
        info "APK built: $apk_abs"
        info "Installing… (confirm the system prompt)"
      else
        warn "Build succeeded but no APK was found under app/build/outputs/apk/$OUT_DIR/."
      fi
    fi
    show_result $code
    ;;

  SYNC)
    ensure_android_sdk || { show_result 1; exit 1; }
    need java "JDK"
    setup_android_sdk
    if [ ! -f ./gradlew ]; then
      error "gradlew not found in the project root."
      exit 1
    fi
    info "Syncing Gradle dependencies (./gradlew --refresh-dependencies tasks $GRADLE_ARGS) ..."
    # $GRADLE_ARGS is intentionally unquoted so multiple flags word-split into separate args.
    run_gradlew --refresh-dependencies tasks $GRADLE_ARGS
    show_result $?
    ;;

  RUST)
    need cargo "Rust (cargo)"
    info "Running: cargo run"
    cargo run
    show_result $?
    ;;

  GO)
    need go "Go"
    info "Running: go run ."
    go run .
    show_result $?
    ;;

  *)
    error "This project type ($TYPE) is not runnable from the run button."
    exit 1
    ;;
esac
