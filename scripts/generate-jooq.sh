#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

docker compose up -d postgres
docker compose run --rm flyway migrate
docker compose run --rm maven mvn -Pgenerate-jooq -pl app -am generate-sources

echo
echo "jOOQ sources regenerated under app/src/generated/java/"
echo "Review the diff with: git diff app/src/generated/java"