#!/usr/bin/env sh
set -eu

if [ "$#" -lt 3 ]; then
  echo "Usage: $0 <debug|release> <android-keystore> <key-alias>"
  exit 2
fi

variant="$1"
keystore="$2"
alias_name="$3"

case "$variant" in
  debug|release) ;;
  *) echo "Variant must be debug or release"; exit 2 ;;
esac

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(dirname "$script_dir")
output_dir="$project_dir/sign/$variant"
temporary_dir=$(mktemp -d "${TMPDIR:-/tmp}/kpilot-sign.XXXXXX")
trap 'rm -rf "$temporary_dir"' EXIT INT TERM

mkdir -p "$output_dir"

if [ -n "${KPILOT_KEYSTORE_PASSWORD:-}" ]; then
  temporary_password=$(openssl rand -hex 32)
  export KPILOT_TEMP_KEYSTORE_PASSWORD="$temporary_password"
  export KPILOT_SOURCE_KEY_PASSWORD="${KPILOT_KEY_PASSWORD:-$KPILOT_KEYSTORE_PASSWORD}"
  keytool -importkeystore \
    -srckeystore "$keystore" \
    -srcstorepass:env KPILOT_KEYSTORE_PASSWORD \
    -srcalias "$alias_name" \
    -srckeypass:env KPILOT_SOURCE_KEY_PASSWORD \
    -destkeystore "$temporary_dir/signing.p12" \
    -deststoretype PKCS12 \
    -deststorepass:env KPILOT_TEMP_KEYSTORE_PASSWORD \
    -destkeypass:env KPILOT_TEMP_KEYSTORE_PASSWORD \
    -noprompt
  openssl pkcs12 -nodes \
    -in "$temporary_dir/signing.p12" \
    -passin env:KPILOT_TEMP_KEYSTORE_PASSWORD \
    -out "$temporary_dir/signing.pem"
else
  keytool -importkeystore \
    -srckeystore "$keystore" \
    -srcalias "$alias_name" \
    -destkeystore "$temporary_dir/signing.p12" \
    -deststoretype PKCS12
  openssl pkcs12 -nodes -in "$temporary_dir/signing.p12" -out "$temporary_dir/signing.pem"
fi

openssl pkey -in "$temporary_dir/signing.pem" -out "$output_dir/private.pem"
openssl x509 -in "$temporary_dir/signing.pem" -out "$output_dir/certificate.pem"
chmod 600 "$output_dir/private.pem"

echo "Created $output_dir/private.pem and $output_dir/certificate.pem"
