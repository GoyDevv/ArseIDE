# auto_setup.sh — one-time environment setup, executed inside the Ubuntu sandbox.
#
# Invoked by com.rk.terminal.AutoSetup on first launch (after the user agrees to Auto Setup).
# Updates the package index, upgrades existing packages, and installs the core tools the IDE
# needs to build/run code.

source "$LOCAL/bin/utils" 2>/dev/null || true

export DEBIAN_FRONTEND=noninteractive

emit_progress() { printf '\n__XEDPROGRESS__ %s %s\n' "$1" "$2"; }
fail() { printf '\n__XEDERROR__ %s\n' "$1"; exit 1; }

APT="apt-get"
command -v apt-get >/dev/null 2>&1 || APT="apt"

emit_progress 3 "Preparing setup"
echo "Starting Xed-Editor auto setup..."

emit_progress 10 "Updating package lists"
$APT update -y 2>&1 || fail "Failed to update package lists (apt update)."

emit_progress 28 "Installing core tools (curl, git, wget)"
$APT install -y curl git wget tar unzip zip nano ca-certificates 2>&1 || fail "Failed to install core tools."

emit_progress 52 "Installing build tools (compiler, make, aapt)"
$APT install -y build-essential pkg-config aapt 2>&1 || fail "Failed to install build tools (build-essential)."

emit_progress 66 "Installing Python (python3, pip, venv)"
$APT install -y python3 python3-pip python3-venv 2>&1 || fail "Failed to install Python."

emit_progress 80 "Installing Node.js & npm"
$APT install -y nodejs npm 2>&1 || fail "Failed to install Node.js/npm."

emit_progress 90 "Installing JDK (OpenJDK 17 & 21)"
$APT install -y openjdk-17-jdk openjdk-21-jdk 2>&1 || $APT install -y openjdk-17-jdk 2>&1 || fail "Failed to install OpenJDK."

emit_progress 95 "Verifying tools"
for tool in curl git wget python3 node npm javac; do
  command -v "$tool" >/dev/null 2>&1 || fail "Tool '$tool' is missing after installation."
done

emit_progress 98 "Cleaning up"
$APT clean 2>&1 || true

emit_progress 100 "Setup finished"
echo "Xed-Editor auto setup completed successfully."
printf '\n__XEDDONE__\n'
