package net.sasasin.sreader.service.autopagerize;

import java.util.Objects;

/** Diagnostic for a URL-matching rule that was not selected. */
public record AutoPagerizeRuleMatchDiagnostic(
    int matchOrder, Target target, AutoPagerizePageAnalyzer.XPathPresence presence) {

  public AutoPagerizeRuleMatchDiagnostic {
    if (matchOrder < 0) {
      throw new IllegalArgumentException("matchOrder must be >= 0");
    }
    Objects.requireNonNull(target, "target must not be null");
    Objects.requireNonNull(presence, "presence must not be null");
    if (presence == AutoPagerizePageAnalyzer.XPathPresence.PRESENT) {
      throw new IllegalArgumentException("PRESENT is not a failed match diagnostic");
    }
  }

  public enum Target {
    NEXT_LINK,
    PAGE_ELEMENT
  }
}
