package net.sasasin.sreader.service;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.ExtractionPlan;

/** Common HTTP/Playwright document acquisition for article and feed-entry probes. */
final class ProbeDocumentFetcher {
  private final HttpFetchService httpFetchService;
  private final PlaywrightHtmlSource playwrightHtmlSource;
  private final FeedReaderProperties properties;

  ProbeDocumentFetcher(
      HttpFetchService httpFetchService,
      PlaywrightHtmlSource playwrightHtmlSource,
      FeedReaderProperties properties) {
    this.httpFetchService = httpFetchService;
    this.playwrightHtmlSource = playwrightHtmlSource;
    this.properties = properties;
  }

  sealed interface FetchOutcome
      permits FetchOutcome.Fetched, FetchOutcome.Skipped, FetchOutcome.Failed {
    record Fetched(FetchedProbeDocument document) implements FetchOutcome {
      public Fetched {
        Objects.requireNonNull(document, "document must not be null");
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

  FetchOutcome fetch(URI requestedUri, ExtractionPlan plan, String failureSubject) {
    return switch (plan.sourceKind()) {
      case HTTP -> fetchHttp(requestedUri, failureSubject);
      case PLAYWRIGHT -> fetchPlaywright(requestedUri, plan, failureSubject);
      default ->
          new FetchOutcome.Failed(
              OperationFailure.of(
                  FailureStage.FETCH_ARTICLE,
                  FailureKind.INVALID_INPUT,
                  failureSubject,
                  "Unexpected source for probe: " + plan.sourceKind()));
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
    } catch (IOException e) {
      FailureKind kind =
          e.getMessage() != null && e.getMessage().contains(" returned HTTP ")
              ? FailureKind.HTTP_STATUS
              : FailureKind.IO;
      return new FetchOutcome.Failed(
          OperationFailure.of(
              FailureStage.FETCH_ARTICLE,
              kind,
              subject,
              "HTTP fetch failed for " + subject + ": " + e.getMessage(),
              e));
    }
  }

  private FetchOutcome fetchPlaywright(URI uri, ExtractionPlan plan, String subject) {
    if (!properties.playwright().enabled()) {
      return new FetchOutcome.Skipped(
          ProbeSkipReason.PLAYWRIGHT_DISABLED,
          "Playwright is required for method but is disabled or misconfigured");
    }
    try {
      RenderedPage page = playwrightHtmlSource.renderPage(uri.toString(), plan.useInfyScroll());
      return new FetchOutcome.Fetched(
          new FetchedProbeDocument(
              uri, page.finalUri() == null ? uri : page.finalUri(), page.html()));
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

  record FetchedProbeDocument(URI requestedUri, URI finalUri, String html) {
    FetchedProbeDocument {
      Objects.requireNonNull(requestedUri, "requestedUri must not be null");
      Objects.requireNonNull(finalUri, "finalUri must not be null");
      Objects.requireNonNull(html, "html must not be null");
    }
  }
}
