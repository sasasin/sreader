#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

docker compose up -d --wait postgres

# One Maven lifecycle: Flyway migrate (initialize) → jOOQ generate (generate-sources)
docker compose run --rm maven \
  mvn -B -Pgenerate-jooq -pl app -am generate-sources

echo
echo "jOOQ sources regenerated under app/src/generated/java/"
echo "Review the diff with:"
echo "  git diff -- app/src/generated/java"
