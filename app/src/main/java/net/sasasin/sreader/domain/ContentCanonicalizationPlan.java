package net.sasasin.sreader.domain;

import java.util.List;

public record ContentCanonicalizationPlan(
    ContentCanonicalizationGroup group,
    ContentCanonicalizationMember survivorValues,
    ContentCanonicalizationMember selectedFullText,
    String survivorId,
    boolean feedConflict) {

  public List<String> memberIds() {
    return group.members().stream().map(ContentCanonicalizationMember::id).toList();
  }

  public boolean merge() {
    return group.members().size() > 1;
  }
}
