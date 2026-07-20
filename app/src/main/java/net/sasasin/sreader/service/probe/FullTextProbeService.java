package net.sasasin.sreader.service.probe;

import com.rometools.rome.feed.synd.SyndEntry;
import java.net.URI;
import java.util.Optional;
import net.sasasin.sreader.domain.FeedEntrySelection;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.FullTextMethod.Definition;
import net.sasasin.sreader.domain.FullTextMethod.HtmlExtractor;
import net.sasasin.sreader.service.extraction.FeedEntryFullTextExtractor;
import net.sasasin.sreader.service.extraction.HtmlTextExtractor;
import net.sasasin.sreader.service.extraction.TextExtractionOutcome;
import net.sasasin.sreader.service.feed.ingestion.FeedDocumentOutcome;
import net.sasasin.sreader.service.feed.ingestion.FeedDocumentService;
import net.sasasin.sreader.service.http.HttpFetchService;
import net.sasasin.sreader.service.http.RedirectResolution;
import net.sasasin.sreader.service.outcome.FailureKind;
import net.sasasin.sreader.service.outcome.FailureStage;
import net.sasasin.sreader.service.outcome.OperationFailure;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

@Service
public class FullTextProbeService {

  private final HttpFetchService httpFetchService;
  private final ProbeDocumentFetcher documentFetcher;
  private final HtmlTextExtractor htmlTextExtractor;
  private final FeedDocumentService feedDocumentService;
  private final FeedEntryPicker feedEntryPicker;
  private final FeedEntryFullTextExtractor feedEntryFullTextExtractor;

  FullTextProbeService(
      HttpFetchService httpFetchService,
      ProbeDocumentFetcher documentFetcher,
      HtmlTextExtractor htmlTextExtractor,
      FeedDocumentService feedDocumentService,
      FeedEntryPicker feedEntryPicker,
      FeedEntryFullTextExtractor feedEntryFullTextExtractor) {
    this.httpFetchService = httpFetchService;
    this.documentFetcher = documentFetcher;
    this.htmlTextExtractor = htmlTextExtractor;
    this.feedDocumentService = feedDocumentService;
    this.feedEntryPicker = feedEntryPicker;
    this.feedEntryFullTextExtractor = feedEntryFullTextExtractor;
  }

  public ProbeOutcome probeArticle(
      URI articleUrl, FullTextMethod method, Optional<String> xpathOverride) {
    if (!method.supportsArticleProbe()) {
      return ProbeOutcome.InvalidRequest.of("--method feed is not supported for probe article");
    }
    // Article-capable methods always expose an ArticleDefinition (sealed hierarchy).
    Definition.ArticleDefinition article =
        method
            .articleDefinition()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Article-capable method missing article definition: " + method.value()));
    return fetchAndExtractArticle(articleUrl, method, article.extractor(), xpathOverride);
  }

  public ProbeOutcome probeFeed(
      URI feedUrl,
      FullTextMethod method,
      FeedEntrySelection selection,
      Optional<String> xpathOverride) {
    FeedDocumentOutcome documentOutcome = feedDocumentService.fetch(feedUrl);
    return switch (documentOutcome) {
      case FeedDocumentOutcome.Failed failed -> new ProbeOutcome.Failed(failed.failure());
      case FeedDocumentOutcome.Fetched fetched ->
          probeSelectedEntry(feedUrl, method, selection, xpathOverride, fetched);
    };
  }

  private ProbeOutcome probeSelectedEntry(
      URI feedUrl,
      FullTextMethod method,
      FeedEntrySelection selection,
      Optional<String> xpathOverride,
      FeedDocumentOutcome.Fetched fetched) {
    boolean requireEntryLink = method.requiresEntryLink();
    Optional<SyndEntry> picked = feedEntryPicker.pick(fetched.feed(), selection, requireEntryLink);
    if (picked.isEmpty()) {
      return new ProbeOutcome.NoMatchingEntry("No feed entry matched selection for " + feedUrl);
    }
    SyndEntry entry = picked.get();
    String entryTitle = entry.getTitle();

    return switch (method.definition()) {
      case Definition.FeedEntry ignored -> {
        if (xpathOverride.isPresent()) {
          yield ProbeOutcome.InvalidRequest.of("--xpath is not applicable for --method feed");
        }
        TextExtractionOutcome textOutcome = feedEntryFullTextExtractor.extract(entry);
        URI finalForResult = feedUrl;
        yield switch (textOutcome) {
          case TextExtractionOutcome.Extracted extracted ->
              new ProbeOutcome.Succeeded(
                  new ProbeDocument(feedUrl, finalForResult, optionalTitle(entryTitle), method),
                  extracted.text());
          case TextExtractionOutcome.NoContent noContent ->
              new ProbeOutcome.NoContent(
                  new ProbeDocument(feedUrl, finalForResult, optionalTitle(entryTitle), method),
                  noContent.reason());
          case TextExtractionOutcome.Skipped skipped ->
              new ProbeOutcome.Skipped(
                  ProbeSkipReason.PLAYWRIGHT_DISABLED, skipped.reason().name());
          case TextExtractionOutcome.Failed failed -> new ProbeOutcome.Failed(failed.failure());
        };
      }
      case Definition.HttpArticle http ->
          probeLinkedArticle(feedUrl, method, entry, entryTitle, xpathOverride, http.extractor());
      case Definition.PlaywrightArticle playwright ->
          probeLinkedArticle(
              feedUrl, method, entry, entryTitle, xpathOverride, playwright.extractor());
    };
  }

  private ProbeOutcome probeLinkedArticle(
      URI feedUrl,
      FullTextMethod method,
      SyndEntry entry,
      String entryTitle,
      Optional<String> xpathOverride,
      HtmlExtractor extractor) {
    if (entry.getLink() == null || entry.getLink().isBlank()) {
      return new ProbeOutcome.NoMatchingEntry("Selected entry has no link for " + feedUrl);
    }

    final URI entrySource;
    try {
      entrySource = URI.create(entry.getLink());
    } catch (IllegalArgumentException e) {
      return new ProbeOutcome.Failed(
          OperationFailure.of(
              FailureStage.RESOLVE_REDIRECT,
              FailureKind.INVALID_INPUT,
              entry.getLink(),
              "Invalid entry link: " + entry.getLink(),
              e));
    }

    RedirectResolution redirect = httpFetchService.resolveRedirect(entrySource);
    if (redirect instanceof RedirectResolution.Fallback fallback
        && fallback.failure().interrupted()) {
      return new ProbeOutcome.Failed(fallback.failure());
    }
    URI entryLink = redirect.effectiveUri();

    ProbeDocumentFetcher.FetchOutcome fetch =
        documentFetcher.fetch(entryLink, method, "entry " + entryLink);
    return switch (fetch) {
      case ProbeDocumentFetcher.FetchOutcome.Skipped skipped ->
          new ProbeOutcome.Skipped(skipped.reason(), skipped.message());
      case ProbeDocumentFetcher.FetchOutcome.Failed failed ->
          new ProbeOutcome.Failed(failed.failure());
      case ProbeDocumentFetcher.FetchOutcome.Fetched fetchedDoc -> {
        Optional<String> title =
            entryTitle != null && !entryTitle.isBlank()
                ? Optional.of(entryTitle)
                : extractTitleFromHtml(fetchedDoc.document().html());
        yield toProbeOutcome(
            feedUrl,
            fetchedDoc.document().finalUri(),
            title,
            method,
            htmlTextExtractor.extract(
                fetchedDoc.document().finalUri().toString(),
                fetchedDoc.document().html(),
                extractor,
                xpathOverride));
      }
    };
  }

  private ProbeOutcome fetchAndExtractArticle(
      URI articleUrl,
      FullTextMethod method,
      HtmlExtractor extractor,
      Optional<String> xpathOverride) {
    ProbeDocumentFetcher.FetchOutcome fetch =
        documentFetcher.fetch(articleUrl, method, articleUrl.toString());
    return switch (fetch) {
      case ProbeDocumentFetcher.FetchOutcome.Skipped skipped ->
          new ProbeOutcome.Skipped(skipped.reason(), skipped.message());
      case ProbeDocumentFetcher.FetchOutcome.Failed failed ->
          new ProbeOutcome.Failed(failed.failure());
      case ProbeDocumentFetcher.FetchOutcome.Fetched fetched ->
          toProbeOutcome(
              articleUrl,
              fetched.document().finalUri(),
              extractTitleFromHtml(fetched.document().html()),
              method,
              htmlTextExtractor.extract(
                  fetched.document().finalUri().toString(),
                  fetched.document().html(),
                  extractor,
                  xpathOverride));
    };
  }

  private ProbeOutcome toProbeOutcome(
      URI inputUrl,
      URI finalUrl,
      Optional<String> title,
      FullTextMethod method,
      TextExtractionOutcome extraction) {
    ProbeDocument document = new ProbeDocument(inputUrl, finalUrl, title, method);
    return switch (extraction) {
      case TextExtractionOutcome.Extracted extracted ->
          new ProbeOutcome.Succeeded(document, extracted.text());
      case TextExtractionOutcome.NoContent noContent ->
          new ProbeOutcome.NoContent(document, noContent.reason());
      case TextExtractionOutcome.Skipped skipped ->
          new ProbeOutcome.Skipped(
              ProbeSkipReason.PLAYWRIGHT_DISABLED,
              "Playwright is required for method but is disabled or misconfigured");
      case TextExtractionOutcome.Failed failed -> {
        if (failed.failure().kind() == FailureKind.INVALID_INPUT
            && failed.failure().stage() == FailureStage.EXTRACT_TEXT) {
          yield new ProbeOutcome.InvalidRequest(
              failed.failure().message(), failed.failure().cause());
        }
        yield new ProbeOutcome.Failed(failed.failure());
      }
    };
  }

  private Optional<String> extractTitleFromHtml(String html) {
    try {
      Document d = Jsoup.parse(html);
      String t = d.title();
      return (t != null && !t.isBlank()) ? Optional.of(t) : Optional.empty();
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  private static Optional<String> optionalTitle(String title) {
    return title == null || title.isBlank() ? Optional.empty() : Optional.of(title);
  }
}
