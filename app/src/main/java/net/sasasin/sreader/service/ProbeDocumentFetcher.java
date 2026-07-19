package net.sasasin.sreader.service;

import java.net.URI;
import net.sasasin.sreader.domain.ExtractionPlan;

/** Common HTTP/Playwright document acquisition for article and feed-entry probes. */
final class ProbeDocumentFetcher {
  private final HttpFetchService httpFetchService;
  private final PlaywrightHtmlSource playwrightHtmlSource;

  ProbeDocumentFetcher(
      HttpFetchService httpFetchService, PlaywrightHtmlSource playwrightHtmlSource) {
    this.httpFetchService = httpFetchService;
    this.playwrightHtmlSource = playwrightHtmlSource;
  }

  FetchedProbeDocument fetch(URI requestedUri, ExtractionPlan plan, String failureSubject) {
    return switch (plan.sourceKind()) {
      case HTTP -> fetchHttp(requestedUri, failureSubject);
      case PLAYWRIGHT -> fetchPlaywright(requestedUri, plan);
      default ->
          throw new IllegalStateException("Unexpected source for probe: " + plan.sourceKind());
    };
  }

  private FetchedProbeDocument fetchHttp(URI uri, String subject) {
    try {
      HttpFetchService.FetchedResource resource = httpFetchService.get(uri);
      return new FetchedProbeDocument(uri, resource.uri(), resource.body());
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new RuntimeException("HTTP fetch failed for " + subject, e);
    }
  }

  private FetchedProbeDocument fetchPlaywright(URI uri, ExtractionPlan plan) {
    try {
      RenderedPage page = playwrightHtmlSource.renderPage(uri.toString(), plan.useInfyScroll());
      return new FetchedProbeDocument(
          uri, page.finalUri() == null ? uri : page.finalUri(), page.html());
    } catch (IllegalStateException e) {
      throw new FullTextProbeService.PlaywrightDisabledException(e.getMessage());
    }
  }

  record FetchedProbeDocument(URI requestedUri, URI finalUri, String html) {}
}
