package net.sasasin.sreader.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ContentCanonicalizationPlan(
    ContentCanonicalizationGroup group,
    ContentCanonicalizationSurvivor survivor,
    Optional<ContentCanonicalizationFullText> selectedFullText,
    boolean feedConflict) {

  public ContentCanonicalizationPlan {
    Objects.requireNonNull(group, "group must not be null");
    Objects.requireNonNull(survivor, "survivor must not be null");
    Objects.requireNonNull(selectedFullText, "selectedFullText must not be null");
  }

  public List<String> memberIds() {
    return group.members().stream().map(ContentCanonicalizationCandidate::id).toList();
  }

  public boolean merge() {
    return group.members().size() > 1;
  }

  /** Survivor header id (MD5 of normalized canonical URL). */
  public String survivorId() {
    return survivor.id();
  }
}
