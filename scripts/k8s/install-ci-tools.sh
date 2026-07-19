#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
tools_dir="${K8S_CI_TOOLS_DIR:-${RUNNER_TEMP:-/tmp}/sreader-k8s-tools}"
source "$repo_root/ops/kubernetes/ci-tools.env"
source "$repo_root/scripts/k8s/versions.sh"
k8s_load_versions "$repo_root/ops/kubernetes/versions.env"

case "$(uname -m)" in
  x86_64) architecture="amd64" ;;
  aarch64|arm64) architecture="arm64" ;;
  *) echo "Unsupported architecture for Kubernetes CI tools: $(uname -m)" >&2; exit 1 ;;
esac
mkdir -p "$tools_dir"
download() { curl --fail --location --retry 3 --silent --show-error "$1" --output "$2"; }
verify_from_checksums() {
  local checksums="$1" filename="$2" artifact="$3" expected
  expected="$(awk -v filename="$filename" '{ candidate=$NF; sub(/^\*/, "", candidate); count=split(candidate, path, "/"); if (path[count] == filename) { print $1; exit } }' "$checksums")"
  [[ "$expected" =~ ^[a-fA-F0-9]{64}$ ]] || { echo "Official checksum missing for $filename" >&2; return 1; }
  printf '%s  %s\n' "$expected" "$artifact" | sha256sum --check --status
}

github_release="https://github.com"
kubeconform_archive="kubeconform-linux-${architecture}.tar.gz"
download "$github_release/yannh/kubeconform/releases/download/$KUBECONFORM_VERSION/$kubeconform_archive" "$tools_dir/$kubeconform_archive"
download "$github_release/yannh/kubeconform/releases/download/$KUBECONFORM_VERSION/CHECKSUMS" "$tools_dir/kubeconform-CHECKSUMS"
verify_from_checksums "$tools_dir/kubeconform-CHECKSUMS" "$kubeconform_archive" "$tools_dir/$kubeconform_archive"
tar -xzf "$tools_dir/$kubeconform_archive" -C "$tools_dir" kubeconform

pluto_version_without_v="${PLUTO_VERSION#v}"
pluto_archive="pluto_${pluto_version_without_v}_linux_${architecture}.tar.gz"
download "$github_release/FairwindsOps/pluto/releases/download/$PLUTO_VERSION/$pluto_archive" "$tools_dir/$pluto_archive"
download "$github_release/FairwindsOps/pluto/releases/download/$PLUTO_VERSION/checksums.txt" "$tools_dir/pluto-checksums.txt"
verify_from_checksums "$tools_dir/pluto-checksums.txt" "$pluto_archive" "$tools_dir/$pluto_archive"
tar -xzf "$tools_dir/$pluto_archive" -C "$tools_dir" pluto

k3d_binary="k3d-linux-${architecture}"
download "$github_release/k3d-io/k3d/releases/download/$K3D_VERSION/$k3d_binary" "$tools_dir/k3d"
download "$github_release/k3d-io/k3d/releases/download/$K3D_VERSION/checksums.txt" "$tools_dir/k3d-checksums.txt"
verify_from_checksums "$tools_dir/k3d-checksums.txt" "$k3d_binary" "$tools_dir/k3d"
chmod +x "$tools_dir/k3d"

kubectl_url="https://dl.k8s.io/release/$KUBERNETES_VERSION/bin/linux/$architecture/kubectl"
download "$kubectl_url" "$tools_dir/kubectl"
download "$kubectl_url.sha256" "$tools_dir/kubectl.sha256"
printf '%s  %s\n' "$(tr -d '[:space:]' < "$tools_dir/kubectl.sha256")" "$tools_dir/kubectl" | sha256sum --check --status
chmod +x "$tools_dir/kubeconform" "$tools_dir/pluto" "$tools_dir/kubectl"
export PATH="$tools_dir:$PATH"
echo "$tools_dir" >> "${GITHUB_PATH:-/dev/null}"
kubeconform -v
pluto version
k3d version
kubectl version --client --output=yaml
