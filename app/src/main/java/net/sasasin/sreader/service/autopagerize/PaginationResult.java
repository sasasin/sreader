package net.sasasin.sreader.service.autopagerize;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.sasasin.sreader.service.outcome.OperationFailure;

/** Sealed outcome of an AutoPagerize pagination run. Partial pages are never a success. */
public sealed interface PaginationResult
    permits PaginationResult.Succeeded, PaginationResult.Failed {

  PageSnapshot firstPageOrNull();

  Optional<CompiledAutoPagerizeRule> matchedRule();

  List<PageSlice> pages();

  PaginationStopReason stopReason();

  default List<AutoPagerizeRuleMatchDiagnostic> ruleMatchDiagnostics() {
    return List.of();
  }

  record Succeeded(
      PageSnapshot firstPage,
      Optional<CompiledAutoPagerizeRule> matchedRule,
      List<PageSlice> pages,
      PaginationStopReason stopReason,
      List<AutoPagerizeRuleMatchDiagnostic> ruleMatchDiagnostics)
      implements PaginationResult {

    public Succeeded(
        PageSnapshot firstPage,
        Optional<CompiledAutoPagerizeRule> matchedRule,
        List<PageSlice> pages,
        PaginationStopReason stopReason) {
      this(firstPage, matchedRule, pages, stopReason, List.of());
    }

    public Succeeded {
      Objects.requireNonNull(firstPage, "firstPage must not be null");
      Objects.requireNonNull(matchedRule, "matchedRule must not be null");
      Objects.requireNonNull(pages, "pages must not be null");
      Objects.requireNonNull(stopReason, "stopReason must not be null");
      Objects.requireNonNull(ruleMatchDiagnostics, "ruleMatchDiagnostics must not be null");
      if (!stopReason.isSuccess()) {
        throw new IllegalArgumentException(
            "Succeeded stopReason must be a success reason: " + stopReason);
      }
      pages = List.copyOf(pages);
      ruleMatchDiagnostics = List.copyOf(ruleMatchDiagnostics);
    }

    @Override
    public PageSnapshot firstPageOrNull() {
      return firstPage;
    }
  }

  record Failed(
      Optional<PageSnapshot> firstPage,
      Optional<CompiledAutoPagerizeRule> matchedRule,
      List<PageSlice> completedPages,
      PaginationStopReason stopReason,
      OperationFailure failure)
      implements PaginationResult {

    public Failed {
      Objects.requireNonNull(firstPage, "firstPage must not be null");
      Objects.requireNonNull(matchedRule, "matchedRule must not be null");
      Objects.requireNonNull(completedPages, "completedPages must not be null");
      Objects.requireNonNull(stopReason, "stopReason must not be null");
      Objects.requireNonNull(failure, "failure must not be null");
      if (stopReason.isSuccess()) {
        throw new IllegalArgumentException(
            "Failed stopReason must not be a success reason: " + stopReason);
      }
      completedPages = List.copyOf(completedPages);
    }

    @Override
    public PageSnapshot firstPageOrNull() {
      return firstPage.orElse(null);
    }

    @Override
    public List<PageSlice> pages() {
      return completedPages;
    }
  }
}
