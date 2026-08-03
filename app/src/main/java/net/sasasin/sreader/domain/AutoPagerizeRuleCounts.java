package net.sasasin.sreader.domain;

/** Count of accepted rules and rejections under one dataset, for integrity checks. */
public record AutoPagerizeRuleCounts(int acceptedRuleCount, int rejectedRuleCount) {

  public AutoPagerizeRuleCounts {
    if (acceptedRuleCount < 0 || rejectedRuleCount < 0) {
      throw new IllegalArgumentException("counts must not be negative");
    }
  }

  public int total() {
    return acceptedRuleCount + rejectedRuleCount;
  }
}
