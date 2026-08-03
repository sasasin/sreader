package net.sasasin.sreader.service.autopagerize;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Selected rule and diagnostics from evaluating URL-matching candidates. */
public record AutoPagerizeRuleMatchResult(
    Optional<CompiledAutoPagerizeRule> matchedRule,
    List<AutoPagerizeRuleMatchDiagnostic> diagnostics) {

  public AutoPagerizeRuleMatchResult {
    Objects.requireNonNull(matchedRule, "matchedRule must not be null");
    Objects.requireNonNull(diagnostics, "diagnostics must not be null");
    diagnostics = List.copyOf(diagnostics);
  }
}
