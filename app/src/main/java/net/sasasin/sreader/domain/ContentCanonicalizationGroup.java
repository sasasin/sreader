package net.sasasin.sreader.domain;

import java.util.List;
import java.util.Objects;

public record ContentCanonicalizationGroup(
    String canonicalUrl, List<ContentCanonicalizationCandidate> members, int exportHistoryRows) {

  public ContentCanonicalizationGroup {
    Objects.requireNonNull(canonicalUrl, "canonicalUrl must not be null");
    Objects.requireNonNull(members, "members must not be null");
    if (members.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("members must not contain null");
    }
    members = List.copyOf(members);
  }
}
