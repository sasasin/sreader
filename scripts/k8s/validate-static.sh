#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$repo_root/scripts/k8s/versions.sh"
k8s_load_versions "$repo_root/ops/kubernetes/versions.env"
manifest_dir="${1:-$repo_root/var/kubernetes-manifests}"
"$repo_root/scripts/k8s/render.sh" "$manifest_dir"
echo "Validating manifests with kubeconform for Kubernetes $KUBERNETES_SCHEMA_VERSION"
kubeconform -strict -summary -kubernetes-version "$KUBERNETES_SCHEMA_VERSION" "$manifest_dir/local.yaml" "$manifest_dir/home.yaml" | tee "$manifest_dir/kubeconform-summary.txt"

pluto_output="$manifest_dir/pluto-report.txt"
echo "Checking deprecated and removed APIs with Pluto for Kubernetes $KUBERNETES_VERSION"
set +e
pluto detect-files --directory "$manifest_dir" --target-versions "k8s=$KUBERNETES_VERSION" --output wide > "$pluto_output"
pluto_status=$?
set -e
cat "$pluto_output"
case "$pluto_status" in
  0) ;;
  2) echo "::warning::Pluto found deprecated APIs; review the report before the next Kubernetes upgrade." ;;
  3) echo "Pluto found APIs removed in $KUBERNETES_VERSION." >&2; exit 1 ;;
  4) echo "Pluto found deprecated APIs without an available replacement." >&2; exit 1 ;;
  *) echo "Pluto execution failed for target $KUBERNETES_VERSION (exit $pluto_status)." >&2; exit 1 ;;
esac
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  { echo "## Kubernetes static validation"; echo; echo "- Target k3s: \`$K3S_VERSION\`"; echo "- Kubernetes schema: \`$KUBERNETES_SCHEMA_VERSION\`"; echo; echo '### Kubeconform'; echo '```text'; cat "$manifest_dir/kubeconform-summary.txt"; echo '```'; echo '### Pluto'; echo '```text'; cat "$pluto_output"; echo '```'; } >> "$GITHUB_STEP_SUMMARY"
fi
