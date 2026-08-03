package net.sasasin.sreader.service.autopagerize;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.sasasin.sreader.service.outcome.FailureKind;
import net.sasasin.sreader.service.outcome.FailureStage;
import net.sasasin.sreader.service.outcome.OperationFailure;
import org.springframework.stereotype.Component;

/**
 * Network-agnostic AutoPagerize pagination engine. Loads pages through {@link ArticlePageSession}
 * and never persists or extracts article text.
 */
@Component
public class AutoPagerizeEngine {

  private final AutoPagerizeRuleMatcher ruleMatcher;
  private final AutoPagerizePageAnalyzer pageAnalyzer;
  private final Clock clock;

  public AutoPagerizeEngine(
      AutoPagerizeRuleMatcher ruleMatcher, AutoPagerizePageAnalyzer pageAnalyzer, Clock clock) {
    this.ruleMatcher = Objects.requireNonNull(ruleMatcher, "ruleMatcher must not be null");
    this.pageAnalyzer = Objects.requireNonNull(pageAnalyzer, "pageAnalyzer must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  public PaginationResult paginate(
      URI startUri,
      ArticlePageSession session,
      AutoPagerizeRuleSnapshot snapshot,
      PaginationPolicy policy) {
    Objects.requireNonNull(startUri, "startUri must not be null");
    Objects.requireNonNull(session, "session must not be null");
    Objects.requireNonNull(snapshot, "snapshot must not be null");
    Objects.requireNonNull(policy, "policy must not be null");

    Instant deadline = clock.instant().plus(policy.totalTimeout());
    if (interrupted()) {
      return failNoPage(
          startUri,
          PaginationStopReason.INTERRUPTED,
          FailureKind.INTERRUPTED,
          "Pagination interrupted before loading the first page");
    }

    PageSnapshot firstPage;
    try {
      firstPage = session.load(startUri);
    } catch (PageLoadException e) {
      if (isInterruptedCause(e)) {
        Thread.currentThread().interrupt();
        return failNoPage(
            startUri,
            PaginationStopReason.INTERRUPTED,
            FailureKind.INTERRUPTED,
            "Pagination interrupted while loading the first page",
            e);
      }
      return failNoPage(
          startUri,
          PaginationStopReason.FETCH_FAILED,
          FailureKind.IO,
          "Failed to load first page: " + message(e),
          e);
    }

    if (timedOut(deadline)) {
      return new PaginationResult.Failed(
          Optional.of(firstPage),
          Optional.empty(),
          List.of(),
          PaginationStopReason.TIMEOUT,
          OperationFailure.of(
              FailureStage.FETCH_ARTICLE,
              FailureKind.IO,
              firstPage.finalUri().toString(),
              "Pagination timed out after loading the first page"));
    }

    long totalBytes = firstPage.byteSize();
    Optional<OperationFailure> sizeFailure = checkPageSize(firstPage, totalBytes, policy);
    if (sizeFailure.isPresent()) {
      PaginationStopReason reason =
          firstPage.byteSize() > policy.maxPageBytes()
              ? PaginationStopReason.MAX_PAGE_BYTES
              : PaginationStopReason.MAX_TOTAL_BYTES;
      return new PaginationResult.Failed(
          Optional.of(firstPage), Optional.empty(), List.of(), reason, sizeFailure.get());
    }

    AutoPagerizeRuleMatchResult matchResult =
        ruleMatcher.findMatchingRuleWithDiagnostics(firstPage, snapshot);
    Optional<CompiledAutoPagerizeRule> matched = matchResult.matchedRule();
    if (matched.isEmpty()) {
      return new PaginationResult.Succeeded(
          firstPage,
          Optional.empty(),
          List.of(PageSlice.withoutPageElement(1, firstPage)),
          PaginationStopReason.NO_MATCHING_RULE,
          matchResult.diagnostics());
    }

    CompiledAutoPagerizeRule rule = matched.get();
    URI originBase = firstPage.finalUri();
    List<PageSlice> pages = new ArrayList<>();
    Set<URI> visited = new HashSet<>();
    Set<String> contentHashes = new HashSet<>();
    markVisited(visited, firstPage);

    PageSnapshot current = firstPage;
    for (int pageNumber = 1; pageNumber <= policy.maxPages(); pageNumber++) {
      if (timedOut(deadline)) {
        return fail(
            firstPage,
            rule,
            pages,
            PaginationStopReason.TIMEOUT,
            FailureKind.IO,
            "Pagination timed out on page " + pageNumber);
      }
      if (interrupted()) {
        return fail(
            firstPage,
            rule,
            pages,
            PaginationStopReason.INTERRUPTED,
            FailureKind.INTERRUPTED,
            "Pagination interrupted on page " + pageNumber);
      }

      // originBase is first page finalUri; only subsequent loads can redirect off-origin here.
      if (pageNumber > 1
          && policy.sameOriginOnly()
          && !PaginationUriSupport.sameOrigin(originBase, current.finalUri())) {
        return fail(
            firstPage,
            rule,
            pages,
            PaginationStopReason.REDIRECT_OFF_ORIGIN,
            FailureKind.INVALID_INPUT,
            "Page final URI left the allowed origin: " + current.finalUri());
      }

      PageAnalysis analysis;
      try {
        analysis = pageAnalyzer.analyze(current, rule);
      } catch (RuntimeException e) {
        return fail(
            firstPage,
            rule,
            pages,
            PaginationStopReason.PAGE_ELEMENT_MISSING,
            FailureKind.EXTRACTION,
            "pageElement missing or invalid on page " + pageNumber + ": " + message(e),
            e);
      }

      if (contentHashes.contains(analysis.pageElementContentHash())) {
        return fail(
            firstPage,
            rule,
            pages,
            PaginationStopReason.CONTENT_LOOP,
            FailureKind.INVALID_INPUT,
            "Repeated pageElement content hash on page " + pageNumber);
      }
      contentHashes.add(analysis.pageElementContentHash());

      Optional<URI> nextUri = analysis.nextUri();
      PageAnalysis.NextLinkIssue nextIssue =
          analysis.nextLinkIssue().orElse(PageAnalysis.NextLinkIssue.MISSING);
      boolean usableNext = nextIssue == PageAnalysis.NextLinkIssue.NONE && nextUri.isPresent();

      PageSlice slice =
          new PageSlice(
              pageNumber,
              current.requestedUri(),
              current.finalUri(),
              current.html(),
              analysis.pageElementOuterHtml(),
              analysis.pageElementText(),
              usableNext ? nextUri : Optional.empty(),
              Optional.of(analysis.pageElementContentHash()),
              current.byteSize());
      pages.add(slice);

      if (nextIssue == PageAnalysis.NextLinkIssue.INVALID_URI
          || nextIssue == PageAnalysis.NextLinkIssue.USERINFO_REJECTED) {
        return fail(
            firstPage,
            rule,
            pages,
            PaginationStopReason.INVALID_NEXT_URI,
            FailureKind.INVALID_INPUT,
            "Invalid next URI on page " + pageNumber);
      }
      if (nextIssue == PageAnalysis.NextLinkIssue.UNSUPPORTED_SCHEME) {
        return fail(
            firstPage,
            rule,
            pages,
            PaginationStopReason.UNSUPPORTED_SCHEME,
            FailureKind.INVALID_INPUT,
            "Unsupported next URI scheme on page " + pageNumber + ": " + nextUri.orElse(null));
      }

      if (!usableNext) {
        return new PaginationResult.Succeeded(
            firstPage,
            Optional.of(rule),
            pages,
            PaginationStopReason.NO_NEXT_LINK,
            matchResult.diagnostics());
      }

      URI next = nextUri.get();
      if (policy.sameOriginOnly() && !PaginationUriSupport.sameOrigin(originBase, next)) {
        return fail(
            firstPage,
            rule,
            pages,
            PaginationStopReason.OFF_ORIGIN,
            FailureKind.INVALID_INPUT,
            "Next URI left the allowed origin: " + next);
      }

      URI visitedKey = PaginationUriSupport.forVisitedComparison(next);
      if (visited.contains(visitedKey)) {
        return fail(
            firstPage,
            rule,
            pages,
            PaginationStopReason.URL_LOOP,
            FailureKind.INVALID_INPUT,
            "URL loop detected for next URI: " + next);
      }

      if (pageNumber >= policy.maxPages()) {
        return fail(
            firstPage,
            rule,
            pages,
            PaginationStopReason.MAX_PAGES,
            FailureKind.INVALID_INPUT,
            "max-pages (" + policy.maxPages() + ") reached with next link still present");
      }

      if (timedOut(deadline)) {
        return fail(
            firstPage,
            rule,
            pages,
            PaginationStopReason.TIMEOUT,
            FailureKind.IO,
            "Pagination timed out before loading page " + (pageNumber + 1));
      }
      if (interrupted()) {
        return fail(
            firstPage,
            rule,
            pages,
            PaginationStopReason.INTERRUPTED,
            FailureKind.INTERRUPTED,
            "Pagination interrupted before loading page " + (pageNumber + 1));
      }

      PageSnapshot nextPage;
      try {
        nextPage = session.load(next);
      } catch (PageLoadException e) {
        if (isInterruptedCause(e)) {
          Thread.currentThread().interrupt();
          return fail(
              firstPage,
              rule,
              pages,
              PaginationStopReason.INTERRUPTED,
              FailureKind.INTERRUPTED,
              "Pagination interrupted while loading page " + (pageNumber + 1),
              e);
        }
        return fail(
            firstPage,
            rule,
            pages,
            PaginationStopReason.FETCH_FAILED,
            FailureKind.IO,
            "Failed to load page " + (pageNumber + 1) + ": " + message(e),
            e);
      }

      if (timedOut(deadline)) {
        return fail(
            firstPage,
            rule,
            pages,
            PaginationStopReason.TIMEOUT,
            FailureKind.IO,
            "Pagination timed out after loading page " + (pageNumber + 1));
      }

      totalBytes += nextPage.byteSize();
      Optional<OperationFailure> nextSizeFailure = checkPageSize(nextPage, totalBytes, policy);
      if (nextSizeFailure.isPresent()) {
        PaginationStopReason reason =
            nextPage.byteSize() > policy.maxPageBytes()
                ? PaginationStopReason.MAX_PAGE_BYTES
                : PaginationStopReason.MAX_TOTAL_BYTES;
        return new PaginationResult.Failed(
            Optional.of(firstPage), Optional.of(rule), pages, reason, nextSizeFailure.get());
      }

      if (policy.sameOriginOnly()
          && !PaginationUriSupport.sameOrigin(originBase, nextPage.finalUri())) {
        return fail(
            firstPage,
            rule,
            pages,
            PaginationStopReason.REDIRECT_OFF_ORIGIN,
            FailureKind.INVALID_INPUT,
            "Redirect final URI left the allowed origin: " + nextPage.finalUri());
      }

      if (isVisited(visited, nextPage)) {
        return fail(
            firstPage,
            rule,
            pages,
            PaginationStopReason.URL_LOOP,
            FailureKind.INVALID_INPUT,
            "URL loop detected after redirect to: " + nextPage.finalUri());
      }

      markVisited(visited, nextPage);
      current = nextPage;
    }

    return fail(
        firstPage,
        rule,
        pages,
        PaginationStopReason.MAX_PAGES,
        FailureKind.INVALID_INPUT,
        "max-pages (" + policy.maxPages() + ") exhausted");
  }

  private static void markVisited(Set<URI> visited, PageSnapshot page) {
    visited.add(PaginationUriSupport.forVisitedComparison(page.requestedUri()));
    visited.add(PaginationUriSupport.forVisitedComparison(page.finalUri()));
  }

  private static boolean isVisited(Set<URI> visited, PageSnapshot page) {
    return visited.contains(PaginationUriSupport.forVisitedComparison(page.requestedUri()))
        || visited.contains(PaginationUriSupport.forVisitedComparison(page.finalUri()));
  }

  private static Optional<OperationFailure> checkPageSize(
      PageSnapshot page, long totalBytes, PaginationPolicy policy) {
    if (page.byteSize() > policy.maxPageBytes()) {
      return Optional.of(
          OperationFailure.of(
              FailureStage.FETCH_ARTICLE,
              FailureKind.INVALID_INPUT,
              page.finalUri().toString(),
              "Page byte size "
                  + page.byteSize()
                  + " exceeds max-page-bytes "
                  + policy.maxPageBytes()));
    }
    if (totalBytes > policy.maxTotalBytes()) {
      return Optional.of(
          OperationFailure.of(
              FailureStage.FETCH_ARTICLE,
              FailureKind.INVALID_INPUT,
              page.finalUri().toString(),
              "Total byte size "
                  + totalBytes
                  + " exceeds max-total-bytes "
                  + policy.maxTotalBytes()));
    }
    return Optional.empty();
  }

  private boolean timedOut(Instant deadline) {
    return !clock.instant().isBefore(deadline);
  }

  private static boolean interrupted() {
    return Thread.currentThread().isInterrupted();
  }

  private static boolean isInterruptedCause(Throwable e) {
    Throwable current = e;
    while (current != null) {
      if (current instanceof InterruptedException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private static String message(Throwable e) {
    String message = e.getMessage();
    return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
  }

  private static PaginationResult.Failed failNoPage(
      URI startUri, PaginationStopReason reason, FailureKind kind, String message) {
    return failNoPage(startUri, reason, kind, message, null);
  }

  private static PaginationResult.Failed failNoPage(
      URI startUri,
      PaginationStopReason reason,
      FailureKind kind,
      String message,
      Throwable cause) {
    OperationFailure failure =
        cause == null
            ? OperationFailure.of(FailureStage.FETCH_ARTICLE, kind, startUri.toString(), message)
            : OperationFailure.of(
                FailureStage.FETCH_ARTICLE, kind, startUri.toString(), message, cause);
    return new PaginationResult.Failed(
        Optional.empty(), Optional.empty(), List.of(), reason, failure);
  }

  private static PaginationResult.Failed fail(
      PageSnapshot firstPage,
      CompiledAutoPagerizeRule rule,
      List<PageSlice> pages,
      PaginationStopReason reason,
      FailureKind kind,
      String message) {
    return fail(firstPage, rule, pages, reason, kind, message, null);
  }

  private static PaginationResult.Failed fail(
      PageSnapshot firstPage,
      CompiledAutoPagerizeRule rule,
      List<PageSlice> pages,
      PaginationStopReason reason,
      FailureKind kind,
      String message,
      Throwable cause) {
    FailureStage stage =
        reason == PaginationStopReason.FETCH_FAILED
            ? FailureStage.FETCH_ARTICLE
            : FailureStage.EXTRACT_TEXT;
    OperationFailure failure =
        cause == null
            ? OperationFailure.of(stage, kind, firstPage.finalUri().toString(), message)
            : OperationFailure.of(stage, kind, firstPage.finalUri().toString(), message, cause);
    return new PaginationResult.Failed(
        Optional.of(firstPage), Optional.of(rule), pages, reason, failure);
  }
}
