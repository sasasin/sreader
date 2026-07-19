#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$script_dir/versions.sh"

candidate_file="${1:?candidate versions file is required}"
base_file="${2:-}"
k8s_load_versions "$candidate_file"
candidate_version="$K3S_VERSION"

if [[ -z "$base_file" || ! -f "$base_file" ]]; then
  echo "K3S upgrade-step check: initial bootstrap; base versions file is absent."
  exit 0
fi

k8s_load_versions "$base_file"
base_version="$K3S_VERSION"

parse_minor() {
  local version="$1" numeric_version major minor patch
  numeric_version="${version#v}"
  numeric_version="${numeric_version%%+*}"
  IFS=. read -r major minor patch <<< "$numeric_version"
  [[ "$major" =~ ^[0-9]+$ && "$minor" =~ ^[0-9]+$ && "$patch" =~ ^[0-9]+$ ]] || return 1
  printf '%s %s\n' "$major" "$minor"
}

read -r base_major base_minor < <(parse_minor "$base_version")
read -r candidate_major candidate_minor < <(parse_minor "$candidate_version")

if (( candidate_major != base_major )); then
  echo "K3S upgrade-step check failed: major change $base_version -> $candidate_version is not allowed." >&2
  exit 1
fi
if (( candidate_minor < base_minor )); then
  echo "K3S upgrade-step check failed: downgrade $base_version -> $candidate_version is not allowed." >&2
  exit 1
fi
if (( candidate_minor > base_minor + 1 )); then
  echo "K3S upgrade-step check failed: minor versions may not be skipped ($base_version -> $candidate_version)." >&2
  exit 1
fi
echo "K3S upgrade-step check passed: $base_version -> $candidate_version"
