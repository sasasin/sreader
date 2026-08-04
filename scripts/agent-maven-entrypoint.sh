#!/bin/sh
set -eu

if [ "$#" -eq 0 ]; then
  set -- mvn clean verify
fi

# /workspace is a Linux named volume. Rebuild it from the current host source
# on every invocation so an agent never runs against a stale source snapshot.
find /workspace -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
tar \
  --exclude='./.git' \
  --exclude='./target' \
  --exclude='./app/bin' \
  --exclude='./app/target' \
  --exclude='./var' \
  -C /source -cf - . \
  | tar -C /workspace -xf -

cd /workspace
exec "$@"
