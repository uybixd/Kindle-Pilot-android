#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "$1" >&2
  exit "${2:-1}"
}

resolve_java_home() {
  local candidate="${JAVA_HOME:-}"
  local android_studio_java="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

  if [[ -z "$candidate" && -x "$android_studio_java/bin/java" ]]; then
    candidate="$android_studio_java"
  fi

  if [[ -z "$candidate" ]]; then
    local java_path
    java_path="$(command -v java || true)"
    if [[ -n "$java_path" ]]; then
      candidate="$("$java_path" -XshowSettings:properties -version 2>&1 \
        | sed -n 's/^[[:space:]]*java.home = //p' \
        | head -n 1)"
    fi
  fi

  [[ -n "$candidate" ]] || fail "Java was not found. Set JAVA_HOME to a JDK installation." 2
  [[ -x "$candidate/bin/java" ]] || fail "JAVA_HOME does not contain an executable java: $candidate" 2
  [[ -x "$candidate/bin/keytool" ]] || fail "JAVA_HOME does not contain an executable keytool: $candidate" 2

  JAVA_HOME="$candidate"
  export JAVA_HOME
  export PATH="$JAVA_HOME/bin:$PATH"
}

resolve_android_sdk() {
  local candidate="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  local local_properties="$project_dir/local.properties"

  if [[ -z "$candidate" && -f "$local_properties" ]]; then
    candidate="$(sed -n 's/^sdk.dir=//p' "$local_properties" | head -n 1)"
  fi

  [[ -n "$candidate" ]] || fail \
    "Android SDK was not found. Set ANDROID_SDK_ROOT or ANDROID_HOME, or configure sdk.dir in local.properties." 2
  [[ -d "$candidate/build-tools" ]] || fail "Android SDK build-tools directory was not found: $candidate/build-tools" 2

  printf '%s\n' "${candidate%/}"
}

read_release_metadata() {
  local metadata_path="$project_dir/app/build/outputs/apk/release/output-metadata.json"
  [[ -f "$metadata_path" ]] || fail "Gradle release metadata was not generated: $metadata_path"

  if command -v jq >/dev/null 2>&1; then
    version_name="$(jq -er '.elements | if length == 1 then .[0].versionName else error("expected one APK output") end' "$metadata_path")"
    apk_output_file="$(jq -er '.elements | if length == 1 then .[0].outputFile else error("expected one APK output") end' "$metadata_path")"
  else
    version_name="$(sed -n 's/^[[:space:]]*"versionName":[[:space:]]*"\([^"]*\)"[,]*[[:space:]]*$/\1/p' "$metadata_path" | head -n 1)"
    apk_output_file="$(sed -n 's/^[[:space:]]*"outputFile":[[:space:]]*"\([^"]*\)"[,]*[[:space:]]*$/\1/p' "$metadata_path" | head -n 1)"
  fi

  [[ "$version_name" =~ ^[0-9A-Za-z._+-]+$ ]] || fail "Invalid versionName in Gradle release metadata: $version_name"
  [[ -n "$apk_output_file" && "$apk_output_file" == "$(basename -- "$apk_output_file")" ]] || \
    fail "Invalid APK output filename in Gradle release metadata: $apk_output_file"
}

use_gui_prompts=false
if [[ "${1:-}" == "--gui" ]]; then
  use_gui_prompts=true
  shift
fi

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 [--gui] /absolute/path/to/kpilot-release.jks [key-alias]" >&2
  exit 2
fi

script_dir="$(cd -- "$(dirname -- "$0")" && pwd)"
project_dir="$(dirname "$script_dir")"
keystore_path="$1"
key_alias="${2:-kpilot-release}"

resolve_java_home
sdk_dir="$(resolve_android_sdk)"

if [[ "$keystore_path" != /* ]]; then
  echo "The keystore path must be absolute." >&2
  exit 2
fi
if [[ ! -f "$keystore_path" ]]; then
  echo "Keystore not found: $keystore_path" >&2
  exit 2
fi

if [[ "$use_gui_prompts" == true ]]; then
  [[ -x /usr/bin/osascript ]] || fail "--gui prompts require macOS osascript." 2
  KPILOT_KEYSTORE_PASSWORD="$(/usr/bin/osascript \
    -e 'display dialog "请输入 KPilot 永久 JKS 的密码" default answer "" with hidden answer buttons {"取消", "继续"} default button "继续" with title "KPilot Release"' \
    -e 'text returned of result')"
  KPILOT_KEY_PASSWORD="$(/usr/bin/osascript \
    -e 'display dialog "请输入 Key 密码；如果与 JKS 密码相同，请留空" default answer "" with hidden answer buttons {"取消", "继续"} default button "继续" with title "KPilot Release"' \
    -e 'text returned of result')"
else
  read -r -s -p "Keystore password: " KPILOT_KEYSTORE_PASSWORD
  printf '\n'
  read -r -s -p "Key password (press Return if it is the same): " KPILOT_KEY_PASSWORD
  printf '\n'
fi

if [[ -z "$KPILOT_KEYSTORE_PASSWORD" ]]; then
  echo "Keystore password cannot be empty." >&2
  exit 2
fi
if [[ -z "$KPILOT_KEY_PASSWORD" ]]; then
  KPILOT_KEY_PASSWORD="$KPILOT_KEYSTORE_PASSWORD"
fi

export KPILOT_KEYSTORE_PATH="$keystore_path"
export KPILOT_KEYSTORE_PASSWORD
export KPILOT_KEY_ALIAS="$key_alias"
export KPILOT_KEY_PASSWORD

cd "$project_dir"
./gradlew --no-configuration-cache testDebugUnitTest lintRelease assembleRelease
read_release_metadata

"$project_dir/xiaomi_band/scripts/export-signing-key.sh" \
  release "$keystore_path" "$key_alias"

unset KPILOT_KEYSTORE_PASSWORD KPILOT_KEY_PASSWORD

cd "$project_dir/xiaomi_band"
npm run release

cd "$project_dir"
apk_path="$project_dir/app/build/outputs/apk/release/$apk_output_file"
rpk_path="$(find "$project_dir/xiaomi_band/dist" -maxdepth 1 -type f -name '*.release.*.rpk' -print -quit)"

if [[ ! -f "$apk_path" ]]; then
  echo "Release APK not found: $apk_path" >&2
  exit 1
fi
if [[ -z "$rpk_path" || ! -f "$rpk_path" ]]; then
  echo "Release RPK was not generated." >&2
  exit 1
fi

apksigner_path="$(find "$sdk_dir/build-tools" -maxdepth 2 -type f -name apksigner -print | sort | tail -n 1)"
if [[ ! -x "$apksigner_path" ]]; then
  echo "apksigner was not found under $sdk_dir/build-tools" >&2
  exit 1
fi

apk_fingerprint="$($apksigner_path verify --print-certs "$apk_path" \
  | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' \
  | head -n 1 \
  | tr '[:upper:]' '[:lower:]')"
rpk_fingerprint="$(openssl x509 \
  -in "$project_dir/xiaomi_band/sign/release/certificate.pem" \
  -noout -fingerprint -sha256 \
  | sed 's/^.*=//' \
  | tr -d ':' \
  | tr '[:upper:]' '[:lower:]')"

if [[ -z "$apk_fingerprint" || "$apk_fingerprint" != "$rpk_fingerprint" ]]; then
  echo "Signing mismatch between APK and RPK certificate." >&2
  echo "APK: $apk_fingerprint" >&2
  echo "RPK: $rpk_fingerprint" >&2
  exit 1
fi

release_dir="$project_dir/release/$version_name"
mkdir -p "$release_dir"
cp "$apk_path" "$release_dir/"
cp "$rpk_path" "$release_dir/"

cd "$release_dir"
apk_name="$(basename "$apk_path")"
rpk_name="$(basename "$rpk_path")"
if command -v shasum >/dev/null 2>&1; then
  shasum -a 256 "$apk_name" "$rpk_name" > SHA256SUMS.txt
elif command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$apk_name" "$rpk_name" > SHA256SUMS.txt
else
  fail "Neither shasum nor sha256sum is available."
fi

printf '\nKPilot %s release package created successfully.\n' "$version_name"
printf 'Signing certificate SHA-256: %s\n' "$apk_fingerprint"
printf 'Output: %s\n' "$release_dir"
