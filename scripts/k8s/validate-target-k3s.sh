#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$repo_root/scripts/k8s/versions.sh"
k8s_load_versions "$repo_root/ops/kubernetes/versions.env"
manifest_dir="${1:-$repo_root/var/kubernetes-manifests}"
cluster_name="sreader-${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-0}-$$"
cluster_name="${cluster_name//[^a-zA-Z0-9-]/-}"
cleanup() { k3d cluster delete "$cluster_name" >/dev/null 2>&1 || true; }
trap cleanup EXIT

"$repo_root/scripts/k8s/render.sh" "$manifest_dir"
echo "Creating k3d cluster $cluster_name with $K3S_IMAGE"
k3d cluster create "$cluster_name" --servers 1 --agents 0 --image "$K3S_IMAGE" --wait --timeout 180s
server_version="$(kubectl version --output=json | grep -o 'v[0-9][^\" ]*+k3s[0-9][^\" ]*' | head -n1 || true)"
if [[ "$server_version" != "$K3S_VERSION" ]]; then
  echo "Target k3s server version mismatch: expected $K3S_VERSION, got ${server_version:-none}" >&2
  exit 1
fi
kubectl wait --for=condition=Ready node --all --timeout=120s
# The namespace must exist for namespaced resources in the server-side dry-runs.
# Create it without client-side apply metadata, which would otherwise conflict
# with server-side apply's migration of last-applied-configuration.
kubectl create namespace sreader
for overlay in local home; do
  kubectl apply --server-side --dry-run=server --validate=strict -f "$manifest_dir/$overlay.yaml"
  echo "Server-side dry-run passed: $overlay"
done
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  { echo "## Target k3s validation"; echo; echo "- Target k3s: \`$K3S_VERSION\`"; echo "- Server version: \`$server_version\`"; echo "- local server-side dry-run: passed"; echo "- home server-side dry-run: passed"; } >> "$GITHUB_STEP_SUMMARY"
fi
