#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
output_dir="${1:-$repo_root/var/kubernetes-manifests}"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

mkdir -p "$output_dir"
cp -R "$repo_root/k8s" "$tmp_dir/k8s"
cp "$tmp_dir/k8s/overlays/home/sreader-db.secret.env.example" "$tmp_dir/k8s/overlays/home/sreader-db.secret.env"

render_overlay() {
  local overlay="$1"
  local output="$output_dir/$overlay.yaml"
  kubectl kustomize "$tmp_dir/k8s/overlays/$overlay" > "$output"
  if [[ ! -s "$output" ]]; then
    echo "Kustomize rendered empty $overlay output: $output" >&2
    return 1
  fi
  local resource_count
  resource_count="$(grep -Ec '^kind: ' "$output" || true)"
  if [[ "$resource_count" == "0" ]]; then
    echo "Kustomize rendered no resources for $overlay: $output" >&2
    return 1
  fi
  echo "Rendered $overlay manifest: $output ($resource_count resources)"
}

render_overlay local
render_overlay home
