package net.sasasin.sreader.service.feed.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.domain.FeedUrl;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.repository.ContentHeaderRepository;
import net.sasasin.sreader.service.article.ArticleUrlCanonicalizer;
import net.sasasin.sreader.service.article.HashIds;
import net.sasasin.sreader.service.extraction.ContentFullTextWriteOutcome;
import net.sasasin.sreader.service.extraction.ContentFullTextWriter;
import net.sasasin.sreader.service.extraction.ExtractionDecision;
import net.sasasin.sreader.service.extraction.ExtractionSource;
import net.sasasin.sreader.service.extraction.FeedEntryFullTextExtractor;
import net.sasasin.sreader.service.extraction.TextExtractionOutcome;
import net.sasasin.sreader.service.http.HttpFetchService;
import net.sasasin.sreader.service.http.RedirectResolution;
import net.sasasin.sreader.service.outcome.FailureKind;
import net.sasasin.sreader.service.outcome.FailureStage;
import net.sasasin.sreader.service.outcome.OperationFailure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FeedEntryImportServiceTest {

  @AfterEach
  void clearInterruptFlag() {
    Thread.interrupted();
  }

  @Test
  void usesCanonicalUrlForIdAndRefreshesOnlyTheFetchUrlForExistingArticle() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    FeedEntryImportService service = service(http, repository);
    URI feed = URI.create("https://example.test/rss.xml");
    URI source = URI.create("https://example.test/article");
    String xml = rss(source.toString());
    URI firstFetch = URI.create("https://publisher.example.test/articles/article?gs=first");
    URI secondFetch = URI.create("https://publisher.example.test/articles/article?gs=second");
    when(http.get(feed)).thenReturn(new HttpFetchService.FetchedResource(feed, xml));
    when(http.resolveRedirect(source))
        .thenReturn(
            new RedirectResolution.Resolved(source, firstFetch),
            new RedirectResolution.Resolved(source, secondFetch));
    when(repository.insertOrRefreshFetchUrl(any()))
        .thenReturn(
            ContentHeaderUpsertOutcome.INSERTED, ContentHeaderUpsertOutcome.EXISTING_REFRESHED);

    FeedImportResult first = service.importEntries(new FeedUrl("feed", feed.toString()));
    FeedImportResult second = service.importEntries(new FeedUrl("feed", feed.toString()));

    assertThat(first).isInstanceOf(FeedImportResult.Completed.class);
    assertThat(first.summary().insertedHeaders()).isEqualTo(1);
    assertThat(second).isInstanceOf(FeedImportResult.Completed.class);
    assertThat(second.summary().existingHeaders()).isEqualTo(1);
    assertThat(second.summary().insertedHeaders()).isZero();

    ArgumentCaptor<ContentHeader> headers = ArgumentCaptor.forClass(ContentHeader.class);
    verify(repository, org.mockito.Mockito.times(2)).insertOrRefreshFetchUrl(headers.capture());
    assertThat(headers.getAllValues())
        .allSatisfy(
            header -> {
              assertThat(header.sourceUrl()).isEqualTo(source.toString());
              assertThat(header.canonicalUrl())
                  .isEqualTo("https://publisher.example.test/articles/article");
              assertThat(header.id()).isEqualTo(HashIds.md5(header.canonicalUrl()));
            });
    assertThat(headers.getAllValues().get(0).fetchUrl()).isEqualTo(firstFetch.toString());
    assertThat(headers.getAllValues().get(1).fetchUrl()).isEqualTo(secondFetch.toString());
  }

  @Test
  void savesFeedBodyOnlyForNewHeader() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    FeedEntryFullTextExtractor extractor = mock(FeedEntryFullTextExtractor.class);
    ContentFullTextWriter writer = mock(ContentFullTextWriter.class);
    FeedEntryImportService service =
        new FeedEntryImportService(
            http, new ArticleUrlCanonicalizer(), repository, extractor, writer);
    URI feed = URI.create("https://example.test/rss.xml");
    URI article = URI.create("https://example.test/article");
    when(http.get(feed))
        .thenReturn(new HttpFetchService.FetchedResource(feed, rss(article.toString())));
    when(http.resolveRedirect(article))
        .thenReturn(new RedirectResolution.Resolved(article, article));
    when(repository.insertOrRefreshFetchUrl(any()))
        .thenReturn(
            ContentHeaderUpsertOutcome.INSERTED, ContentHeaderUpsertOutcome.EXISTING_REFRESHED);
    when(extractor.extract(any()))
        .thenReturn(
            new TextExtractionOutcome.Extracted(
                "Feed body", ExtractionDecision.of(ExtractionSource.FEED)));
    when(writer.saveIfAbsent(any(), org.mockito.ArgumentMatchers.eq("Feed body")))
        .thenReturn(ContentFullTextWriteOutcome.INSERTED);

    FeedUrl feedUrl =
        new FeedUrl(
            "feed",
            feed.toString(),
            net.sasasin.sreader.domain.FeedStatus.ACTIVE,
            null,
            null,
            null,
            FullTextMethod.FEED);
    FeedImportResult first = service.importEntries(feedUrl);
    FeedImportResult second = service.importEntries(feedUrl);

    assertThat(first.summary().insertedHeaders()).isEqualTo(1);
    assertThat(first.summary().feedTextsInserted()).isEqualTo(1);
    assertThat(second.summary().existingHeaders()).isEqualTo(1);
    verify(writer)
        .saveIfAbsent(any(ContentHeader.class), org.mockito.ArgumentMatchers.eq("Feed body"));
  }

  @Test
  void feedFetchFailureIsFailedNotZeroCompleted() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    URI feed = URI.create("https://example.test/rss.xml");
    when(http.get(feed)).thenThrow(new IOException("network"));

    FeedImportResult result =
        service(http, repository).importEntries(new FeedUrl("feed", feed.toString()));

    assertThat(result).isInstanceOf(FeedImportResult.Failed.class);
    FeedImportResult.Failed failed = (FeedImportResult.Failed) result;
    assertThat(failed.failure().stage()).isEqualTo(FailureStage.FETCH_FEED);
    assertThat(failed.summary().insertedHeaders()).isZero();
    verify(repository, never()).insertOrRefreshFetchUrl(any());
  }

  @Test
  void redirectFallbackIsCountedAndDoesNotFailImport() throws Exception {
    HttpFetchService http = mock(HttpFetchService.class);
    ContentHeaderRepository repository = mock(ContentHeaderRepository.class);
    URI feed = URI.create("https://example.test/rss.xml");
    URI source = URI.create("https://example.test/article");
    when(http.get(feed))
        .thenReturn(new HttpFetchService.FetchedResource(feed, rss(source.toString())));
    when(http.resolveRedirect(source))
        .thenReturn(
            new RedirectResolution.Fallback(
                source,
                OperationFailure.of(
                    FailureStage.RESOLVE_REDIRECT,
                    FailureKind.IO,
                    source.toString(),
                    "redirect failed")));
    when(repository.insertOrRefreshFetchUrl(any())).thenReturn(ContentHeaderUpsertOutcome.INSERTED);

    FeedImportResult result =
        service(http, repository).importEntries(new FeedUrl("feed", feed.toString()));

    assertThat(result).isInstanceOf(FeedImportResult.Completed.class);
    assertThat(result.summary().insertedHeaders()).isEqualTo(1);
    assertThat(result.summary().redirectFallbacks()).isEqualTo(1);
  }

  private FeedEntryImportService service(
      HttpFetchService http, ContentHeaderRepository repository) {
    return new FeedEntryImportService(
        http,
        new ArticleUrlCanonicalizer("publisher.example.test", "/articles/"),
        repository,
        mock(FeedEntryFullTextExtractor.class),
        mock(ContentFullTextWriter.class));
  }

  private String rss(String link) {
    return "<rss version=\"2.0\"><channel><title>Feed</title><item><title>Article</title><link>"
        + link
        + "</link></item></channel></rss>";
  }
}
