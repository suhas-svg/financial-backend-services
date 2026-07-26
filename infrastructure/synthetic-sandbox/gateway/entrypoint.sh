#!/bin/sh
set -eu

cert=/etc/nginx/tls/tls.crt
key=/etc/nginx/tls/tls.key

origin_port=${SANDBOX_HTTPS_PORT:-8443}
case "$origin_port" in
  ''|*[!0-9]*)
    echo "SANDBOX_HTTPS_PORT must be a numeric TCP port" >&2
    exit 1
    ;;
esac
if [ "$origin_port" -lt 1 ] || [ "$origin_port" -gt 65535 ]; then
  echo "SANDBOX_HTTPS_PORT must be between 1 and 65535" >&2
  exit 1
fi
sed "s/__SANDBOX_HTTPS_PORT__/$origin_port/g" \
  /etc/nginx/nginx.conf.template > /tmp/nginx.conf

if [ ! -s "$cert" ] || [ ! -s "$key" ]; then
  umask 077
  openssl req -x509 -newkey rsa:3072 -sha256 -nodes -days 30 \
    -subj "/CN=localhost/O=Financial Synthetic Sandbox" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" \
    -keyout "$key" -out "$cert"
fi

exec nginx -c /tmp/nginx.conf -g "daemon off;"
