package net.sasasin.sreader.service.probe;

import com.rometools.rome.feed.synd.SyndEntry;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.sasasin.sreader.domain.FeedEntrySelection;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.FullTextMethod.Definition;
import net.sasasin.sreader.domain.FullTextMethod.HtmlExtractor;
import net.sasasin.sreader.service.autopagerize.PageSnapshot;
import net.sasasin.sreader.service.extraction.FeedEntryFullTextExtractor;
import net.sasasin.sreader.service.extraction.HtmlTextExtractor;
import net.sasasin.sreader.service.extraction.PaginatedExtractionResult;
import net.sasasin.sreader.service.extraction.PaginatedHtmlTextExtractor;
import net.sasasin.sreader.service.extraction.PaginationMetadata;
import net.sasasin.sreader.service.extraction.PaginationMetadataFactory;
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
  private final PaginatedHtmlTextExtractor paginatedHtmlTextExtractor;
  private final FeedDocumentService feedDocumentService;
  private final FeedEntryPicker feedEntryPicker;
  private final FeedEntryFullTextExtractor feedEntryFullTextExtractor;

  FullTextProbeService(
      HttpFetchService httpFetchService,
      ProbeDocumentFetcher documentFetcher,
      HtmlTextExtractor htmlTextExtractor,
      PaginatedHtmlTextExtractor paginatedHtmlTextExtractor,
      FeedDocumentService feedDocumentService,
      FeedEntryPicker feedEntryPicker,
      FeedEntryFullTextExtractor feedEntryFullTextExtractor) {
    this.httpFetchService = httpFetchService;
    this.documentFetcher = documentFetcher;
    this.htmlTextExtractor = htmlTextExtractor;
    this.paginatedHtmlTextExtractor = paginatedHtmlTextExtractor;
    this.feedDocumentService = feedDocumentService;
    this.feedEntryPicker = feedEntryPicker;
    this.feedEntryFullTextExtractor = feedEntryFullTextExtractor;
  }

  public ProbeOutcome probeArticle(
      URI articleUrl, FullTextMethod method, Optional<String> xpathOverride) {
    return probeArticle(articleUrl, method, xpathOverride, Optional.empty());
  }

  public ProbeOutcome probeArticle(
      URI articleUrl,
      FullTextMethod method,
      Optional<String> xpathOverride,
      Optional<Long> autopagerizeDatasetId) {
    Objects.requireNonNull(autopagerizeDatasetId, "autopagerizeDatasetId must not be null");
    if (!method.supportsArticleProbe()) {
      return ProbeOutcome.InvalidRequest.of("--method feed is not supported for probe article");
    }
    Optional<ProbeOutcome> datasetOptionError =
        validateDatasetOption(method, autopagerizeDatasetId);
    if (datasetOptionError.isPresent()) {
      return datasetOptionError.get();
    }
    Definition.ArticleDefinition article =
        method
            .articleDefinition()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Article-capable method missing article definition: " + method.value()));
    return fetchAndExtractArticle(
        articleUrl, method, article.extractor(), xpathOverride, autopagerizeDatasetId);
  }

  public ProbeOutcome probeFeed(
      URI feedUrl,
      FullTextMethod method,
      FeedEntrySelection selection,
      Optional<String> xpathOverride) {
    return probeFeed(feedUrl, method, selection, xpathOverride, Optional.empty());
  }

  public ProbeOutcome probeFeed(
      URI feedUrl,
      FullTextMethod method,
      FeedEntrySelection selection,
      Optional<String> xpathOverride,
      Optional<Long> autopagerizeDatasetId) {
    Objects.requireNonNull(autopagerizeDatasetId, "autopagerizeDatasetId must not be null");
    Optional<ProbeOutcome> datasetOptionError =
        validateDatasetOption(method, autopagerizeDatasetId);
    if (datasetOptionError.isPresent()) {
      return datasetOptionError.get();
    }
    FeedDocumentOutcome documentOutcome = feedDocumentService.fetch(feedUrl);
    return switch (documentOutcome) {
      case FeedDocumentOutcome.Failed failed -> new ProbeOutcome.Failed(failed.failure());
      case FeedDocumentOutcome.Fetched fetched ->
          probeSelectedEntry(
              feedUrl, method, selection, xpathOverride, fetched, autopagerizeDatasetId);
    };
  }

  private static Optional<ProbeOutcome> validateDatasetOption(
      FullTextMethod method, Optional<Long> datasetId) {
    if (datasetId.isPresent() && !method.usesAutopagerize()) {
      return Optional.of(
          ProbeOutcome.InvalidRequest.of(
              "--autopagerize-dataset-id is only valid with AutoPagerize methods"));
    }
    return Optional.empty();
  }

  private ProbeOutcome probeSelectedEntry(
      URI feedUrl,
      FullTextMethod method,
      FeedEntrySelection selection,
      Optional<String> xpathOverride,
      FeedDocumentOutcome.Fetched fetched,
      Optional<Long> autopagerizeDatasetId) {
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
                  extracted.text(),
                  extracted.decision(),
                  Optional.empty());
          case TextExtractionOutcome.NoContent noContent ->
              new ProbeOutcome.NoContent(
                  new ProbeDocument(feedUrl, finalForResult, optionalTitle(entryTitle), method),
                  noContent.reason());
          case TextExtractionOutcome.Skipped skipped ->
              new ProbeOutcome.Skipped(
                  ProbeSkipReason.PLAYWRIGHT_DISABLED, skipped.reason().name());
          case TextExtractionOutcome.Failed failed ->
              new ProbeOutcome.Failed(failed.failure(), failed.pagination());
        };
      }
      case Definition.HttpArticle http ->
          probeLinkedArticle(
              feedUrl, method, entry, xpathOverride, http.extractor(), autopagerizeDatasetId);
      case Definition.PlaywrightArticle playwright ->
          probeLinkedArticle(
              feedUrl, method, entry, xpathOverride, playwright.extractor(), autopagerizeDatasetId);
    };
  }

  private ProbeOutcome probeLinkedArticle(
      URI feedUrl,
      FullTextMethod method,
      SyndEntry entry,
      Optional<String> xpathOverride,
      HtmlExtractor extractor,
      Optional<Long> autopagerizeDatasetId) {
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
        documentFetcher.fetch(entryLink, method, "entry " + entryLink, autopagerizeDatasetId);
    return toProbeOutcome(fetch, feedUrl, method, extractor, xpathOverride, Optional.empty());
  }

  private ProbeOutcome fetchAndExtractArticle(
      URI articleUrl,
      FullTextMethod method,
      HtmlExtractor extractor,
      Optional<String> xpathOverride,
      Optional<Long> autopagerizeDatasetId) {
    ProbeDocumentFetcher.FetchOutcome fetch =
        documentFetcher.fetch(articleUrl, method, articleUrl.toString(), autopagerizeDatasetId);
    return toProbeOutcome(fetch, articleUrl, method, extractor, xpathOverride, Optional.empty());
  }

  private ProbeOutcome toProbeOutcome(
      ProbeDocumentFetcher.FetchOutcome fetch,
      URI inputUrl,
      FullTextMethod method,
      HtmlExtractor extractor,
      Optional<String> xpathOverride,
      Optional<String> preferredTitle) {
    return switch (fetch) {
      case ProbeDocumentFetcher.FetchOutcome.Skipped skipped ->
          new ProbeOutcome.Skipped(skipped.reason(), skipped.message());
      case ProbeDocumentFetcher.FetchOutcome.Failed failed ->
          mapFetchFailure(failed.failure(), failed.pagination());
      case ProbeDocumentFetcher.FetchOutcome.Fetched fetched -> {
        Optional<String> title =
            preferredTitle.or(() -> extractTitleFromHtml(fetched.document().html()));
        TextExtractionOutcome extraction =
            htmlTextExtractor.extract(
                fetched.document().finalUri().toString(),
                fetched.document().html(),
                extractor,
                xpathOverride);
        yield toProbeOutcome(
            inputUrl,
            fetched.document().finalUri(),
            title,
            method,
            extraction instanceof TextExtractionOutcome.Extracted extracted
                ? extracted.withExtractedUrl(fetched.document().finalUri().toString())
                : extraction);
      }
      case ProbeDocumentFetcher.FetchOutcome.Paginated paginated -> {
        PageSnapshot firstPage = paginated.pagination().firstPage();
        Optional<String> title = preferredTitle.or(() -> extractTitleFromHtml(firstPage.html()));
        TextExtractionOutcome extraction;
        try {
          PaginatedExtractionResult result =
              paginatedHtmlTextExtractor.extract(paginated.pagination(), extractor, xpathOverride);
          PaginationMetadata metadata =
              PaginationMetadataFactory.fromSucceeded(
                  paginated.pagination(),
                  paginated.snapshot(),
                  result.contributions(),
                  paginated.explicitDatasetSelection());
          String firstFinalUrl = firstPage.finalUri().toString();
          extraction =
              switch (result.outcome()) {
                case TextExtractionOutcome.Extracted extracted ->
                    extracted.withPagination(metadata).withExtractedUrl(firstFinalUrl);
                case TextExtractionOutcome.NoContent noContent ->
                    noContent.withPagination(metadata);
                case TextExtractionOutcome.Skipped skipped -> skipped;
                case TextExtractionOutcome.Failed failed -> failed.withPagination(metadata);
              };
        } catch (RuntimeException e) {
          extraction =
              new TextExtractionOutcome.Failed(
                      OperationFailure.of(
                          FailureStage.EXTRACT_TEXT,
                          FailureKind.EXTRACTION,
                          inputUrl.toString(),
                          "AutoPagerize probe extraction failed for " + inputUrl,
                          e))
                  .withPagination(
                      PaginationMetadataFactory.fromSucceeded(
                          paginated.pagination(),
                          paginated.snapshot(),
                          List.of(),
                          paginated.explicitDatasetSelection()));
        }
        yield toProbeOutcome(inputUrl, firstPage.finalUri(), title, method, extraction);
      }
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
          new ProbeOutcome.Succeeded(
              document, extracted.text(), extracted.decision(), extracted.pagination());
      case TextExtractionOutcome.NoContent noContent ->
          new ProbeOutcome.NoContent(document, noContent.reason(), noContent.pagination());
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
        // Missing dataset is a configuration/usage-style failure for probe UX.
        if (failed.failure().kind() == FailureKind.INVALID_INPUT
            && failed.failure().stage() == FailureStage.LOAD_AUTOPAGERIZE_DATABASE) {
          yield new ProbeOutcome.InvalidRequest(
              failed.failure().message(), failed.failure().cause());
        }
        yield new ProbeOutcome.Failed(failed.failure(), failed.pagination());
      }
    };
  }

  private static ProbeOutcome mapFetchFailure(
      OperationFailure failure, Optional<PaginationMetadata> pagination) {
    // Missing/unknown dataset is a configuration/usage error for probe CLI exit code mapping.
    if (failure.kind() == FailureKind.INVALID_INPUT
        && failure.stage() == FailureStage.LOAD_AUTOPAGERIZE_DATABASE) {
      return new ProbeOutcome.InvalidRequest(failure.message(), failure.cause());
    }
    return new ProbeOutcome.Failed(failure, pagination);
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
