package net.sasasin.sreader.service.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.service.extraction.ExtractionDecision;
import net.sasasin.sreader.service.extraction.ExtractionSource;
import net.sasasin.sreader.service.extraction.FeedEntryFullTextExtractor;
import net.sasasin.sreader.service.extraction.HtmlTextExtractor;
import net.sasasin.sreader.service.extraction.TextExtractionOutcome;
import net.sasasin.sreader.service.extraction.browser.PlaywrightHtmlSource;
import net.sasasin.sreader.service.extraction.browser.PlaywrightRenderMode;
import net.sasasin.sreader.service.feed.ingestion.FeedDocumentOutcome;
import net.sasasin.sreader.service.feed.ingestion.FeedDocumentService;
import net.sasasin.sreader.service.http.HttpFetchService;
import net.sasasin.sreader.service.http.RedirectResolution;
import net.sasasin.sreader.service.outcome.FailureKind;
import net.sasasin.sreader.service.outcome.FailureStage;
import net.sasasin.sreader.service.outcome.OperationFailure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Extra branch coverage for P0-3 outcome paths (probe). */
class OutcomeBranchCoverageProbeTest {

  @AfterEach
  void clearInterrupt() {
    Thread.interrupted();
  }

  @Test
  void probeInterruptedFailurePreservesInterruptInCliMappingPath() {
    ProbeOutcome.Failed interrupted =
        new ProbeOutcome.Failed(
            OperationFailure.of(
                FailureStage.FETCH_ARTICLE,
                FailureKind.INTERRUPTED,
                "https://x",
                "stopped",
                new InterruptedException("stop")));
    assertThat(interrupted.failure().interrupted()).isTrue();
  }

  @Test
  void probeDocumentFetcherCoversHttpStatusPlaywrightAndUnexpectedSource() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    PlaywrightHtmlSource playwright = mock(PlaywrightHtmlSource.class);
    ProbeDocumentFetcher fetcher = new ProbeDocumentFetcher(http, playwright, properties(true));

    when(http.get(URI.create("https://a")))
        .thenThrow(new IOException("GET https://a returned HTTP 500"));
    ProbeDocumentFetcher.FetchOutcome.Failed status =
        (ProbeDocumentFetcher.FetchOutcome.Failed)
            fetcher.fetch(
                URI.create("https://a"),
                net.sasasin.sreader.domain.ExtractionPlan.from(FullTextMethod.HTTP),
                "https://a");
    assertThat(status.failure().kind()).isEqualTo(FailureKind.HTTP_STATUS);

    when(playwright.renderPage(any(), eq(PlaywrightRenderMode.STANDARD)))
        .thenThrow(new RuntimeException("render"));
    ProbeDocumentFetcher.FetchOutcome.Failed render =
        (ProbeDocumentFetcher.FetchOutcome.Failed)
            fetcher.fetch(
                URI.create("https://a"),
                net.sasasin.sreader.domain.ExtractionPlan.from(FullTextMethod.PLAYWRIGHT),
                "https://a");
    assertThat(render.failure().kind()).isEqualTo(FailureKind.RENDER);

    ProbeDocumentFetcher.FetchOutcome.Failed unexpected =
        (ProbeDocumentFetcher.FetchOutcome.Failed)
            fetcher.fetch(
                URI.create("https://a"),
                net.sasasin.sreader.domain.ExtractionPlan.from(FullTextMethod.FEED),
                "https://a");
    assertThat(unexpected.failure().kind()).isEqualTo(FailureKind.INVALID_INPUT);
  }

  @Test
  void probeServiceFeedFetchFailureAndInvalidLinkAndEmptyTitle() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    PlaywrightHtmlSource playwright = mock(PlaywrightHtmlSource.class);
    HtmlTextExtractor extractor = mock(HtmlTextExtractor.class);
    FeedDocumentService documents = mock(FeedDocumentService.class);
    FeedEntryPicker picker = mock(FeedEntryPicker.class);
    FeedEntryFullTextExtractor feedExtractor = mock(FeedEntryFullTextExtractor.class);
    FullTextProbeService service =
        new FullTextProbeService(
            http, playwright, extractor, documents, picker, feedExtractor, properties(true));

    when(documents.fetch(URI.create("https://feed")))
        .thenReturn(
            new FeedDocumentOutcome.Failed(
                OperationFailure.of(
                    FailureStage.FETCH_FEED, FailureKind.IO, "https://feed", "down")));
    assertThat(
            service.probeFeed(
                URI.create("https://feed"),
                FullTextMethod.HTTP,
                net.sasasin.sreader.domain.FeedEntrySelection.first(),
                Optional.empty()))
        .isInstanceOf(ProbeOutcome.Failed.class);

    SyndFeed feed = mock(SyndFeed.class);
    SyndEntry entry = mock(SyndEntry.class);
    when(entry.getLink()).thenReturn("http://[");
    when(entry.getTitle()).thenReturn(" ");
    when(documents.fetch(URI.create("https://feed")))
        .thenReturn(new FeedDocumentOutcome.Fetched(feed));
    when(picker.pick(feed, net.sasasin.sreader.domain.FeedEntrySelection.first(), true))
        .thenReturn(Optional.of(entry));
    ProbeOutcome.Failed badLink =
        (ProbeOutcome.Failed)
            service.probeFeed(
                URI.create("https://feed"),
                FullTextMethod.HTTP,
                net.sasasin.sreader.domain.FeedEntrySelection.first(),
                Optional.empty());
    assertThat(badLink.failure().kind()).isEqualTo(FailureKind.INVALID_INPUT);

    URI link = URI.create("https://entry");
    when(entry.getLink()).thenReturn(link.toString());
    when(entry.getTitle()).thenReturn(null);
    when(http.resolveRedirect(link)).thenReturn(new RedirectResolution.Resolved(link, link));
    when(http.get(link))
        .thenReturn(new HttpFetchService.FetchedResource(link, "<html><title></title></html>"));
    when(extractor.extract(any(), any(), any(), any()))
        .thenReturn(
            new TextExtractionOutcome.Extracted(
                "body", ExtractionDecision.of(ExtractionSource.BODY_TEXT)));
    ProbeOutcome.Succeeded succeeded =
        (ProbeOutcome.Succeeded)
            service.probeFeed(
                URI.create("https://feed"),
                FullTextMethod.HTTP,
                net.sasasin.sreader.domain.FeedEntrySelection.first(),
                Optional.empty());
    assertThat(succeeded.document().title()).isEmpty();
  }

  private FeedReaderProperties properties(boolean playwrightEnabled) {
    return new FeedReaderProperties(
        new FeedReaderProperties.Scheduler(false, "0 */15 * * * *"),
        new FeedReaderProperties.Job(false),
        new FeedReaderProperties.Http("test", Duration.ofSeconds(1), Duration.ofSeconds(1), 0),
        new FeedReaderProperties.Playwright(
            playwrightEnabled,
            true,
            1280,
            1600,
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            null,
            null,
            1,
            1,
            Duration.ofMillis(1)),
        null,
        List.of());
  }
}
