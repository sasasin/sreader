package net.sasasin.sreader.service.probe;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.FullTextMethod.Definition;
import net.sasasin.sreader.domain.FullTextMethod.PaginationMode;
import net.sasasin.sreader.service.autopagerize.ArticlePageSession;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeCatalogException;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeEngine;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeRuleCatalog;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeRuleSnapshot;
import net.sasasin.sreader.service.autopagerize.PaginationResult;
import net.sasasin.sreader.service.extraction.browser.PlaywrightHtmlSource;
import net.sasasin.sreader.service.extraction.browser.RenderedPage;
import net.sasasin.sreader.service.http.HttpFetchService;
import net.sasasin.sreader.service.http.HttpStatusException;
import net.sasasin.sreader.service.outcome.FailureKind;
import net.sasasin.sreader.service.outcome.FailureStage;
import net.sasasin.sreader.service.outcome.OperationFailure;
import net.sasasin.sreader.service.outcome.OutcomePreconditions;

/** Common HTTP/Playwright document acquisition for article and feed-entry probes. */
final class ProbeDocumentFetcher {
  private final HttpFetchService httpFetchService;
  private final PlaywrightHtmlSource playwrightHtmlSource;
  private final FeedReaderProperties properties;
  private final AutoPagerizeRuleCatalog autoPagerizeRuleCatalog;
  private final AutoPagerizeEngine autoPagerizeEngine;

  ProbeDocumentFetcher(
      HttpFetchService httpFetchService,
      PlaywrightHtmlSource playwrightHtmlSource,
      FeedReaderProperties properties,
      AutoPagerizeRuleCatalog autoPagerizeRuleCatalog,
      AutoPagerizeEngine autoPagerizeEngine) {
    this.httpFetchService = httpFetchService;
    this.playwrightHtmlSource = playwrightHtmlSource;
    this.properties = properties;
    this.autoPagerizeRuleCatalog = autoPagerizeRuleCatalog;
    this.autoPagerizeEngine = autoPagerizeEngine;
  }

  sealed interface FetchOutcome
      permits FetchOutcome.Fetched,
          FetchOutcome.Paginated,
          FetchOutcome.Skipped,
          FetchOutcome.Failed {
    record Fetched(FetchedProbeDocument document) implements FetchOutcome {
      public Fetched {
        Objects.requireNonNull(document, "document must not be null");
      }
    }

    record Paginated(PaginationResult.Succeeded pagination) implements FetchOutcome {
      public Paginated {
        Objects.requireNonNull(pagination, "pagination must not be null");
      }
    }

    record Skipped(ProbeSkipReason reason, String message) implements FetchOutcome {
      public Skipped {
        Objects.requireNonNull(reason, "reason must not be null");
        message = OutcomePreconditions.requireNonBlank(message, "message");
      }
    }

    record Failed(OperationFailure failure) implements FetchOutcome {
      public Failed {
        Objects.requireNonNull(failure, "failure must not be null");
      }
    }
  }

  FetchOutcome fetch(URI requestedUri, FullTextMethod method, String failureSubject) {
    return switch (method.definition()) {
      case Definition.HttpArticle ignored -> fetchHttp(requestedUri, failureSubject);
      case Definition.PlaywrightArticle playwright ->
          playwright.pagination() == PaginationMode.AUTOPAGERIZE
              ? fetchPlaywrightAutopagerize(requestedUri, failureSubject)
              : fetchPlaywright(requestedUri, playwright, failureSubject);
      case Definition.FeedEntry ignored ->
          new FetchOutcome.Failed(
              OperationFailure.of(
                  FailureStage.FETCH_ARTICLE,
                  FailureKind.INVALID_INPUT,
                  failureSubject,
                  "Unexpected source for probe: feed entry content"));
    };
  }

  private FetchOutcome fetchHttp(URI uri, String subject) {
    try {
      HttpFetchService.FetchedResource resource = httpFetchService.get(uri);
      return new FetchOutcome.Fetched(
          new FetchedProbeDocument(uri, resource.uri(), resource.body()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new FetchOutcome.Failed(
          OperationFailure.of(
              FailureStage.FETCH_ARTICLE,
              FailureKind.INTERRUPTED,
              subject,
              "HTTP fetch interrupted for " + subject,
              e));
    } catch (HttpStatusException e) {
      return new FetchOutcome.Failed(
          OperationFailure.of(
              FailureStage.FETCH_ARTICLE,
              FailureKind.HTTP_STATUS,
              subject,
              "HTTP fetch failed for " + subject + ": " + e.getMessage(),
              e));
    } catch (IOException e) {
      return new FetchOutcome.Failed(
          OperationFailure.of(
              FailureStage.FETCH_ARTICLE,
              FailureKind.IO,
              subject,
              "HTTP fetch failed for " + subject + ": " + e.getMessage(),
              e));
    }
  }

  private FetchOutcome fetchPlaywright(
      URI uri, Definition.PlaywrightArticle definition, String subject) {
    if (!properties.playwright().enabled()) {
      return new FetchOutcome.Skipped(
          ProbeSkipReason.PLAYWRIGHT_DISABLED,
          "Playwright is required for method but is disabled or misconfigured");
    }
    try {
      RenderedPage page = playwrightHtmlSource.renderPage(uri, definition.mode());
      return new FetchOutcome.Fetched(new FetchedProbeDocument(uri, page.finalUri(), page.html()));
    } catch (RuntimeException e) {
      return new FetchOutcome.Failed(
          OperationFailure.of(
              FailureStage.RENDER_ARTICLE,
              FailureKind.RENDER,
              subject,
              "Playwright render failed for " + subject + ": " + e.getMessage(),
              e));
    }
  }

  private FetchOutcome fetchPlaywrightAutopagerize(URI uri, String subject) {
    if (!properties.playwright().enabled()) {
      return new FetchOutcome.Skipped(
          ProbeSkipReason.PLAYWRIGHT_DISABLED,
          "Playwright is required for method but is disabled or misconfigured");
    }

    final AutoPagerizeRuleSnapshot snapshot;
    try {
      Optional<AutoPagerizeRuleSnapshot> active = autoPagerizeRuleCatalog.getActiveSnapshot();
      if (active.isEmpty()) {
        return new FetchOutcome.Failed(
            OperationFailure.of(
                FailureStage.LOAD_AUTOPAGERIZE_DATABASE,
                FailureKind.INVALID_INPUT,
                subject,
                "No active AutoPagerize dataset; import and activate a local SITEINFO JSON first"));
      }
      snapshot = active.get();
    } catch (AutoPagerizeCatalogException e) {
      return new FetchOutcome.Failed(
          OperationFailure.of(
              FailureStage.MATCH_AUTOPAGERIZE_RULE,
              FailureKind.UNEXPECTED,
              subject,
              "Failed to load or compile active AutoPagerize rules: " + e.getMessage(),
              e));
    } catch (RuntimeException e) {
      return new FetchOutcome.Failed(
          OperationFailure.of(
              FailureStage.LOAD_AUTOPAGERIZE_DATABASE,
              FailureKind.UNEXPECTED,
              subject,
              "Failed to load active AutoPagerize dataset: " + e.getMessage(),
              e));
    }

    try {
      return playwrightHtmlSource.withStandardSession(
          session -> paginate(uri, subject, snapshot, session));
    } catch (RuntimeException e) {
      return new FetchOutcome.Failed(
          OperationFailure.of(
              FailureStage.RENDER_ARTICLE,
              FailureKind.RENDER,
              subject,
              "Playwright AutoPagerize render failed for " + subject + ": " + e.getMessage(),
              e));
    }
  }

  private FetchOutcome paginate(
      URI uri, String subject, AutoPagerizeRuleSnapshot snapshot, ArticlePageSession session) {
    try {
      PaginationResult result =
          autoPagerizeEngine.paginate(
              uri, session, snapshot, properties.autopagerize().toPaginationPolicy());
      if (result instanceof PaginationResult.Failed failed) {
        return new FetchOutcome.Failed(failed.failure());
      }
      return new FetchOutcome.Paginated((PaginationResult.Succeeded) result);
    } catch (RuntimeException e) {
      return new FetchOutcome.Failed(
          OperationFailure.of(
              FailureStage.MATCH_AUTOPAGERIZE_RULE,
              FailureKind.UNEXPECTED,
              subject,
              "Playwright AutoPagerize rule matching failed for " + subject + ": " + e.getMessage(),
              e));
    }
  }

  record FetchedProbeDocument(URI requestedUri, URI finalUri, String html) {
    FetchedProbeDocument {
      Objects.requireNonNull(requestedUri, "requestedUri must not be null");
      Objects.requireNonNull(finalUri, "finalUri must not be null");
      Objects.requireNonNull(html, "html must not be null");
    }
  }
}
