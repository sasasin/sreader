#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
candidate="$tmp_dir/candidate.env"
base="$tmp_dir/base.env"
release_versions="$tmp_dir/release-versions.txt"

assert_derived_versions() {
  local version="$K3S_VERSION"
  local major minor patch build

  [[ "$version" =~ ^v([0-9]+)\.([0-9]+)\.([0-9]+)\+k3s([0-9]+)$ ]]
  major="${BASH_REMATCH[1]}"
  minor="${BASH_REMATCH[2]}"
  patch="${BASH_REMATCH[3]}"
  build="${BASH_REMATCH[4]}"

  [[ "$KUBERNETES_VERSION" == "v$major.$minor.$patch" ]]
  [[ "$KUBERNETES_SCHEMA_VERSION" == "$major.$minor.$patch" ]]
  [[ "$K3S_IMAGE_TAG" == "v$major.$minor.$patch-k3s$build" ]]
  [[ "$K3S_IMAGE" == "rancher/k3s:$K3S_IMAGE_TAG" ]]
}

write_version_file() {
  local version_file="$1"
  local version="$2"

  printf 'K3S_VERSION=%s\n' "$version" > "$version_file"
}

fetch_release_versions() {
  local page=1 response_headers response_json
  local -a curl_headers=(-H 'Accept: application/vnd.github+json')

  if [[ -n "${GITHUB_TOKEN:-}" ]]; then
    curl_headers+=(-H "Authorization: Bearer $GITHUB_TOKEN")
  fi

  response_headers="$tmp_dir/release-headers.txt"
  response_json="$tmp_dir/release-page.json"

  while true; do
    curl -fsSL \
      -D "$response_headers" \
      -o "$response_json" \
      "${curl_headers[@]}" \
      "https://api.github.com/repos/k3s-io/k3s/releases?per_page=100&page=$page"

    jq -r '
      .[]
      | select(.draft == false)
      | select(.prerelease == false)
      | .tag_name
      | select(test("^v[0-9]+\\.[0-9]+\\.[0-9]+\\+k3s[0-9]+$"))
    ' "$response_json" >> "$release_versions"
    # GitHub REST API pagination is limited to the first 1,000 results.
    if ((page == 10)) || ! grep -Fq 'rel="next"' "$response_headers"; then
      break
    fi
    page=$((page + 1))
  done

  sort -Vu -o "$release_versions" "$release_versions"
}

source "$repo_root/scripts/k8s/versions.sh"
k8s_load_versions "$repo_root/ops/kubernetes/versions.env"
target_version="$K3S_VERSION"
target_major="${BASH_REMATCH[1]}"
target_minor="${BASH_REMATCH[2]}"

fetch_release_versions
grep -Fxq "$target_version" "$release_versions"

patch_base=""
minor_base=""
skipped_minor_base=""
target_found=false
while IFS= read -r version; do
  if [[ "$version" == "$target_version" ]]; then
    target_found=true
    continue
  fi
  if [[ "$version" =~ ^v([0-9]+)\.([0-9]+)\.([0-9]+)\+k3s([0-9]+)$ ]]; then
    major="${BASH_REMATCH[1]}"
    minor="${BASH_REMATCH[2]}"
    if [[ "$target_found" == false && "$major" == "$target_major" && "$minor" == "$target_minor" ]]; then
      patch_base="$version"
    fi
    if [[ "$major" == "$target_major" && "$minor" -eq $((target_minor - 1)) ]]; then
      minor_base="$version"
    fi
    if [[ "$major" == "$target_major" && "$minor" -eq $((target_minor - 2)) ]]; then
      skipped_minor_base="$version"
    fi
  fi
done < "$release_versions"

[[ "$target_found" == true ]]
[[ -n "$patch_base" ]]
[[ -n "$minor_base" ]]
[[ -n "$skipped_minor_base" ]]

write_version_file "$candidate" "$target_version"
k8s_load_versions "$candidate"
assert_derived_versions
"$repo_root/scripts/k8s/check-upgrade-step.sh" "$candidate"
write_version_file "$base" "$patch_base"
"$repo_root/scripts/k8s/check-upgrade-step.sh" "$candidate" "$base"
write_version_file "$base" "$minor_base"
"$repo_root/scripts/k8s/check-upgrade-step.sh" "$candidate" "$base"
write_version_file "$base" "$skipped_minor_base"
if "$repo_root/scripts/k8s/check-upgrade-step.sh" "$candidate" "$base"; then exit 1; fi
write_version_file "$candidate" "$minor_base"
write_version_file "$base" "$target_version"
if "$repo_root/scripts/k8s/check-upgrade-step.sh" "$candidate" "$base"; then exit 1; fi
printf 'K3S_VERSION=invalid\n' > "$candidate"
if "$repo_root/scripts/k8s/check-upgrade-step.sh" "$candidate"; then exit 1; fi
k8s_load_versions "$repo_root/ops/kubernetes/versions.env"
assert_derived_versions
echo "Kubernetes script tests passed."
