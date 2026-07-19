#!/usr/bin/env bash
# Shared parser for k8s/ops/versions.env. Source this file; it exports values.

set -euo pipefail

k8s_load_versions() {
  local versions_file="${1:?versions file is required}"
  local matches version_match

  if [[ ! -f "$versions_file" ]]; then
    echo "Kubernetes versions file not found: $versions_file" >&2
    return 1
  fi

  matches="$(grep -Ec '^K3S_VERSION=' "$versions_file" || true)"
  if [[ "$matches" != "1" ]]; then
    echo "Expected exactly one K3S_VERSION entry in $versions_file; found $matches" >&2
    return 1
  fi

  version_match="$(grep -E '^K3S_VERSION=' "$versions_file")"
  K3S_VERSION="${version_match#K3S_VERSION=}"
  if [[ ! "$K3S_VERSION" =~ ^v([0-9]+)\.([0-9]+)\.([0-9]+)\+k3s([0-9]+)$ ]]; then
    echo "Invalid K3S_VERSION '$K3S_VERSION' in $versions_file; expected vMAJOR.MINOR.PATCH+k3sBUILD" >&2
    return 1
  fi

  KUBERNETES_VERSION="v${BASH_REMATCH[1]}.${BASH_REMATCH[2]}.${BASH_REMATCH[3]}"
  KUBERNETES_SCHEMA_VERSION="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}.${BASH_REMATCH[3]}"
  K3S_IMAGE_TAG="${KUBERNETES_VERSION}-k3s${BASH_REMATCH[4]}"
  K3S_IMAGE="rancher/k3s:${K3S_IMAGE_TAG}"
  export K3S_VERSION KUBERNETES_VERSION KUBERNETES_SCHEMA_VERSION K3S_IMAGE_TAG K3S_IMAGE
}
