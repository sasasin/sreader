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
import net.sasasin.sreader.service.extraction.PaginationMetadata;
import net.sasasin.sreader.service.extraction.PaginationMetadataFactory;
import net.sasasin.sreader.service.extraction.browser.PlaywrightHtmlSource;
import net.sasasin.sreader.service.extraction.browser.PlaywrightSessionFailure;
import net.sasasin.sreader.service.extraction.browser.RenderedPage;
import net.sasasin.sreader.service.http.HttpArticlePageSessionFactory;
import net.sasasin.sreader.service.http.HttpFetchService;
import net.sasasin.sreader.service.http.HttpStatusException;
import net.sasasin.sreader.service.outcome.FailureKind;
import net.sasasin.sreader.service.outcome.FailureStage;
import net.sasasin.sreader.service.outcome.OperationFailure;
import net.sasasin.sreader.service.outcome.OutcomePreconditions;

/** Common HTTP/Playwright document acquisition for article and feed-entry probes. */
final class ProbeDocumentFetcher {
  private final HttpFetchService httpFetchService;
  private final HttpArticlePageSessionFactory httpArticlePageSessionFactory;
  private final PlaywrightHtmlSource playwrightHtmlSource;
  private final FeedReaderProperties properties;
  private final AutoPagerizeRuleCatalog autoPagerizeRuleCatalog;
  private final AutoPagerizeEngine autoPagerizeEngine;

  ProbeDocumentFetcher(
      HttpFetchService httpFetchService,
      HttpArticlePageSessionFactory httpArticlePageSessionFactory,
      PlaywrightHtmlSource playwrightHtmlSource,
      FeedReaderProperties properties,
      AutoPagerizeRuleCatalog autoPagerizeRuleCatalog,
      AutoPagerizeEngine autoPagerizeEngine) {
    this.httpFetchService = httpFetchService;
    this.httpArticlePageSessionFactory = httpArticlePageSessionFactory;
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

    record Paginated(
        PaginationResult.Succeeded pagination,
        AutoPagerizeRuleSnapshot snapshot,
        boolean explicitDatasetSelection)
        implements FetchOutcome {
      public Paginated {
        Objects.requireNonNull(pagination, "pagination must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
      }
    }

    record Skipped(ProbeSkipReason reason, String message) implements FetchOutcome {
      public Skipped {
        Objects.requireNonNull(reason, "reason must not be null");
        message = OutcomePreconditions.requireNonBlank(message, "message");
      }
    }

    record Failed(OperationFailure failure, Optional<PaginationMetadata> pagination)
        implements FetchOutcome {
      Failed(OperationFailure failure) {
        this(failure, Optional.empty());
      }

      public Failed {
        Objects.requireNonNull(failure, "failure must not be null");
        Objects.requireNonNull(pagination, "pagination must not be null");
      }
    }
  }

  FetchOutcome fetch(
      URI requestedUri,
      FullTextMethod method,
      String failureSubject,
      Optional<Long> autopagerizeDatasetId) {
    Objects.requireNonNull(autopagerizeDatasetId, "autopagerizeDatasetId must not be null");
    return switch (method.definition()) {
      case Definition.HttpArticle http ->
          http.pagination() == PaginationMode.AUTOPAGERIZE
              ? fetchHttpAutopagerize(requestedUri, failureSubject, autopagerizeDatasetId)
              : fetchHttp(requestedUri, failureSubject);
      case Definition.PlaywrightArticle playwright ->
          playwright.pagination() == PaginationMode.AUTOPAGERIZE
              ? fetchPlaywrightAutopagerize(requestedUri, failureSubject, autopagerizeDatasetId)
              : fetchPlaywright(requestedUri, failureSubject);
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

  private FetchOutcome fetchPlaywright(URI uri, String subject) {
    if (!properties.playwright().enabled()) {
      return new FetchOutcome.Skipped(
          ProbeSkipReason.PLAYWRIGHT_DISABLED,
          "Playwright is required for method but is disabled or misconfigured");
    }
    try {
      RenderedPage page = playwrightHtmlSource.renderPage(uri);
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

  private FetchOutcome fetchHttpAutopagerize(URI uri, String subject, Optional<Long> datasetId) {
    SnapshotResolution resolution = resolveSnapshot(subject, datasetId);
    if (resolution instanceof SnapshotResolution.Failed failed) {
      return new FetchOutcome.Failed(failed.failure());
    }
    AutoPagerizeRuleSnapshot snapshot = ((SnapshotResolution.Ready) resolution).snapshot();
    boolean explicit = ((SnapshotResolution.Ready) resolution).explicitSelection();

    try (ArticlePageSession session = httpArticlePageSessionFactory.open()) {
      return paginate(uri, subject, snapshot, session, explicit);
    } catch (RuntimeException e) {
      return new FetchOutcome.Failed(
          OperationFailure.of(
              FailureStage.FETCH_ARTICLE_PAGE,
              FailureKind.UNEXPECTED,
              subject,
              "HTTP AutoPagerize probe failed for " + subject + ": " + e.getMessage(),
              e));
    }
  }

  private FetchOutcome fetchPlaywrightAutopagerize(
      URI uri, String subject, Optional<Long> datasetId) {
    if (!properties.playwright().enabled()) {
      return new FetchOutcome.Skipped(
          ProbeSkipReason.PLAYWRIGHT_DISABLED,
          "Playwright is required for method but is disabled or misconfigured");
    }

    SnapshotResolution resolution = resolveSnapshot(subject, datasetId);
    if (resolution instanceof SnapshotResolution.Failed failed) {
      return new FetchOutcome.Failed(failed.failure());
    }
    AutoPagerizeRuleSnapshot snapshot = ((SnapshotResolution.Ready) resolution).snapshot();
    boolean explicit = ((SnapshotResolution.Ready) resolution).explicitSelection();

    try {
      return playwrightHtmlSource.withStandardSession(
          session -> paginate(uri, subject, snapshot, session, explicit));
    } catch (PlaywrightSessionFailure e) {
      return new FetchOutcome.Failed(e.failure());
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
      URI uri,
      String subject,
      AutoPagerizeRuleSnapshot snapshot,
      ArticlePageSession session,
      boolean explicitSelection) {
    try {
      PaginationResult result =
          autoPagerizeEngine.paginate(
              uri, session, snapshot, properties.autopagerize().toPaginationPolicy());
      if (result instanceof PaginationResult.Failed failed) {
        PaginationMetadata meta =
            PaginationMetadataFactory.fromFailed(failed, snapshot, explicitSelection);
        // Prefer returning Failed with diagnostics over throwing so probe can print page traces.
        return new FetchOutcome.Failed(failed.failure(), Optional.of(meta));
      }
      return new FetchOutcome.Paginated(
          (PaginationResult.Succeeded) result, snapshot, explicitSelection);
    } catch (RuntimeException e) {
      return new FetchOutcome.Failed(
          OperationFailure.of(
              FailureStage.MATCH_AUTOPAGERIZE_RULE,
              FailureKind.UNEXPECTED,
              subject,
              "AutoPagerize rule matching failed for " + subject + ": " + e.getMessage(),
              e));
    }
  }

  private SnapshotResolution resolveSnapshot(String subject, Optional<Long> datasetId) {
    if (datasetId.isPresent()) {
      try {
        AutoPagerizeRuleSnapshot snapshot = autoPagerizeRuleCatalog.getSnapshot(datasetId.get());
        return new SnapshotResolution.Ready(snapshot, true);
      } catch (AutoPagerizeCatalogException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("not found")) {
          return new SnapshotResolution.Failed(
              OperationFailure.of(
                  FailureStage.LOAD_AUTOPAGERIZE_DATABASE,
                  FailureKind.INVALID_INPUT,
                  subject,
                  "AutoPagerize dataset not found: " + datasetId.get()));
        }
        return new SnapshotResolution.Failed(
            OperationFailure.of(
                FailureStage.MATCH_AUTOPAGERIZE_RULE,
                FailureKind.UNEXPECTED,
                subject,
                "Failed to load or compile AutoPagerize dataset "
                    + datasetId.get()
                    + ": "
                    + e.getMessage(),
                e));
      } catch (RuntimeException e) {
        return new SnapshotResolution.Failed(
            OperationFailure.of(
                FailureStage.LOAD_AUTOPAGERIZE_DATABASE,
                FailureKind.UNEXPECTED,
                subject,
                "Failed to load AutoPagerize dataset " + datasetId.get() + ": " + e.getMessage(),
                e));
      }
    }

    try {
      Optional<AutoPagerizeRuleSnapshot> active = autoPagerizeRuleCatalog.getActiveSnapshot();
      if (active.isEmpty()) {
        return new SnapshotResolution.Failed(
            OperationFailure.of(
                FailureStage.LOAD_AUTOPAGERIZE_DATABASE,
                FailureKind.INVALID_INPUT,
                subject,
                "No active AutoPagerize dataset; import and activate a local SITEINFO JSON first"));
      }
      return new SnapshotResolution.Ready(active.get(), false);
    } catch (AutoPagerizeCatalogException e) {
      return new SnapshotResolution.Failed(
          OperationFailure.of(
              FailureStage.MATCH_AUTOPAGERIZE_RULE,
              FailureKind.UNEXPECTED,
              subject,
              "Failed to load or compile active AutoPagerize rules: " + e.getMessage(),
              e));
    } catch (RuntimeException e) {
      return new SnapshotResolution.Failed(
          OperationFailure.of(
              FailureStage.LOAD_AUTOPAGERIZE_DATABASE,
              FailureKind.UNEXPECTED,
              subject,
              "Failed to load active AutoPagerize dataset: " + e.getMessage(),
              e));
    }
  }

  private sealed interface SnapshotResolution
      permits SnapshotResolution.Ready, SnapshotResolution.Failed {
    record Ready(AutoPagerizeRuleSnapshot snapshot, boolean explicitSelection)
        implements SnapshotResolution {
      public Ready {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
      }
    }

    record Failed(OperationFailure failure) implements SnapshotResolution {
      public Failed {
        Objects.requireNonNull(failure, "failure must not be null");
      }
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
