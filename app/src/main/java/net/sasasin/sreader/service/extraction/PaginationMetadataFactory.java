package net.sasasin.sreader.service.extraction;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeRuleSnapshot;
import net.sasasin.sreader.service.autopagerize.CompiledAutoPagerizeRule;
import net.sasasin.sreader.service.autopagerize.PageSlice;
import net.sasasin.sreader.service.autopagerize.PaginationResult;
import net.sasasin.sreader.service.autopagerize.PaginationStopReason;

/** Builds {@link PaginationMetadata} from engine results for persistence and probe diagnostics. */
public final class PaginationMetadataFactory {

  private PaginationMetadataFactory() {}

  public static PaginationMetadata fromSucceeded(
      PaginationResult.Succeeded pagination,
      AutoPagerizeRuleSnapshot snapshot,
      List<PageTextContribution> contributions,
      boolean explicitDatasetSelection) {
    Objects.requireNonNull(pagination, "pagination must not be null");
    Objects.requireNonNull(snapshot, "snapshot must not be null");
    Objects.requireNonNull(contributions, "contributions must not be null");
    return build(
        snapshot,
        pagination.matchedRule(),
        pagination.pages(),
        pagination.stopReason(),
        true,
        Optional.empty(),
        contributions,
        explicitDatasetSelection);
  }

  public static PaginationMetadata fromFailed(
      PaginationResult.Failed failed,
      AutoPagerizeRuleSnapshot snapshot,
      boolean explicitDatasetSelection) {
    Objects.requireNonNull(failed, "failed must not be null");
    Objects.requireNonNull(snapshot, "snapshot must not be null");
    List<PageSlice> completedPages = failed.completedPages();
    if (completedPages.isEmpty() && failed.firstPage().isPresent()) {
      completedPages = List.of(PageSlice.withoutPageElement(1, failed.firstPage().get()));
    }
    return build(
        snapshot,
        failed.matchedRule(),
        completedPages,
        failed.stopReason(),
        false,
        failed.failedRequestedUri(),
        List.of(),
        explicitDatasetSelection);
  }

  private static PaginationMetadata build(
      AutoPagerizeRuleSnapshot snapshot,
      Optional<CompiledAutoPagerizeRule> rule,
      List<PageSlice> pages,
      PaginationStopReason stopReason,
      boolean complete,
      Optional<URI> failedRequestedUrl,
      List<PageTextContribution> contributions,
      boolean explicitDatasetSelection) {
    List<PaginationPageTrace> traces =
        pages.stream()
            .map(
                page ->
                    new PaginationPageTrace(
                        page.pageNumber(), page.requestedUri(), page.finalUri(), page.byteSize()))
            .toList();
    int pageCount = pages.isEmpty() ? 0 : pages.size();
    return new PaginationMetadata(
        snapshot.datasetId(),
        snapshot.sourceSha256(),
        snapshot.importerVersion(),
        explicitDatasetSelection,
        rule.map(CompiledAutoPagerizeRule::ordinal),
        rule.map(CompiledAutoPagerizeRule::name),
        rule.map(CompiledAutoPagerizeRule::urlPatternSource),
        rule.map(CompiledAutoPagerizeRule::nextLinkXpath),
        rule.map(CompiledAutoPagerizeRule::pageElementXpath),
        pageCount,
        stopReason,
        complete,
        traces,
        failedRequestedUrl,
        contributions);
  }
}
