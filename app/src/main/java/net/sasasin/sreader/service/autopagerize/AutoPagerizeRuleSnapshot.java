package net.sasasin.sreader.service.autopagerize;

import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of compiled AutoPagerize rules for one dataset, ordered by {@code
 * match_order}.
 */
public record AutoPagerizeRuleSnapshot(
    long datasetId,
    String sourceSha256,
    int importerVersion,
    List<CompiledAutoPagerizeRule> rules) {

  public AutoPagerizeRuleSnapshot {
    Objects.requireNonNull(sourceSha256, "sourceSha256 must not be null");
    Objects.requireNonNull(rules, "rules must not be null");
    if (!sourceSha256.matches("^[0-9a-f]{64}$")) {
      throw new IllegalArgumentException("sourceSha256 must be 64 lowercase hex characters");
    }
    if (importerVersion < 1) {
      throw new IllegalArgumentException("importerVersion must be >= 1");
    }
    rules = List.copyOf(rules);
  }

  public int size() {
    return rules.size();
  }
}
