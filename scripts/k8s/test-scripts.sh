#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
candidate="$tmp_dir/candidate.env"
base="$tmp_dir/base.env"
printf 'K3S_VERSION=v1.35.5+k3s1\n' > "$candidate"
"$repo_root/scripts/k8s/check-upgrade-step.sh" "$candidate"
printf 'K3S_VERSION=v1.35.4+k3s1\n' > "$base"
"$repo_root/scripts/k8s/check-upgrade-step.sh" "$candidate" "$base"
printf 'K3S_VERSION=v1.36.0+k3s1\n' > "$candidate"
"$repo_root/scripts/k8s/check-upgrade-step.sh" "$candidate" "$base"
printf 'K3S_VERSION=v1.37.0+k3s1\n' > "$candidate"
if "$repo_root/scripts/k8s/check-upgrade-step.sh" "$candidate" "$base"; then exit 1; fi
printf 'K3S_VERSION=v1.34.0+k3s1\n' > "$candidate"
if "$repo_root/scripts/k8s/check-upgrade-step.sh" "$candidate" "$base"; then exit 1; fi
printf 'K3S_VERSION=invalid\n' > "$candidate"
if "$repo_root/scripts/k8s/check-upgrade-step.sh" "$candidate"; then exit 1; fi
source "$repo_root/scripts/k8s/versions.sh"
k8s_load_versions "$repo_root/ops/kubernetes/versions.env"
[[ "$K3S_VERSION" == "v1.35.5+k3s1" && "$KUBERNETES_VERSION" == "v1.35.5" && "$KUBERNETES_SCHEMA_VERSION" == "1.35.5" && "$K3S_IMAGE_TAG" == "v1.35.5-k3s1" && "$K3S_IMAGE" == "rancher/k3s:v1.35.5-k3s1" ]]
echo "Kubernetes script tests passed."
