package net.sasasin.sreader.service.extraction;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.FullTextMethod.Definition;
import net.sasasin.sreader.domain.FullTextMethod.HtmlExtractor;
import net.sasasin.sreader.domain.FullTextMethod.PaginationMode;
import net.sasasin.sreader.domain.PendingFullTextTarget;
import net.sasasin.sreader.repository.ContentHeaderRepository;
import net.sasasin.sreader.service.autopagerize.ArticlePageSession;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeCatalogException;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeEngine;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeRuleCatalog;
import net.sasasin.sreader.service.autopagerize.AutoPagerizeRuleSnapshot;
import net.sasasin.sreader.service.autopagerize.PaginationResult;
import net.sasasin.sreader.service.extraction.browser.PlaywrightHtmlSource;
import net.sasasin.sreader.service.extraction.browser.PlaywrightSessionFailure;
import net.sasasin.sreader.service.http.HttpArticlePageSessionFactory;
import net.sasasin.sreader.service.http.HttpFetchService;
import net.sasasin.sreader.service.http.HttpStatusException;
import net.sasasin.sreader.service.outcome.BatchStopReason;
import net.sasasin.sreader.service.outcome.FailureKind;
import net.sasasin.sreader.service.outcome.FailureStage;
import net.sasasin.sreader.service.outcome.OperationFailure;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FullTextExtractionService {

  private static final Logger logger = LoggerFactory.getLogger(FullTextExtractionService.class);

  private final ContentHeaderRepository contentHeaderRepository;
  private final ContentFullTextWriter contentFullTextWriter;
  private final HtmlTextExtractor htmlTextExtractor;
  private final PaginatedHtmlTextExtractor paginatedHtmlTextExtractor;
  private final HttpFetchService httpFetchService;
  private final HttpArticlePageSessionFactory httpArticlePageSessionFactory;
  private final AutoPagerizeRuleCatalog autoPagerizeRuleCatalog;
  private final AutoPagerizeEngine autoPagerizeEngine;
  private final PlaywrightHtmlSource playwrightHtmlSource;
  private final FeedReaderProperties properties;

  public FullTextExtractionService(
      ContentHeaderRepository contentHeaderRepository,
      ContentFullTextWriter contentFullTextWriter,
      HtmlTextExtractor htmlTextExtractor,
      PaginatedHtmlTextExtractor paginatedHtmlTextExtractor,
      HttpFetchService httpFetchService,
      HttpArticlePageSessionFactory httpArticlePageSessionFactory,
      AutoPagerizeRuleCatalog autoPagerizeRuleCatalog,
      AutoPagerizeEngine autoPagerizeEngine,
      PlaywrightHtmlSource playwrightHtmlSource,
      FeedReaderProperties properties) {
    this.contentHeaderRepository = contentHeaderRepository;
    this.contentFullTextWriter = contentFullTextWriter;
    this.htmlTextExtractor = htmlTextExtractor;
    this.paginatedHtmlTextExtractor = paginatedHtmlTextExtractor;
    this.httpFetchService = httpFetchService;
    this.httpArticlePageSessionFactory = httpArticlePageSessionFactory;
    this.autoPagerizeRuleCatalog = autoPagerizeRuleCatalog;
    this.autoPagerizeEngine = autoPagerizeEngine;
    this.playwrightHtmlSource = playwrightHtmlSource;
    this.properties = properties;
  }

  public FullTextExtractionBatchResult extractPending(int limit) {
    List<PendingFullTextTarget> targets =
        contentHeaderRepository.findWithoutFullTextForUrlExtraction(limit);
    int inserted = 0;
    int alreadyPresent = 0;
    int noContent = 0;
    int skipped = 0;
    int failed = 0;
    Optional<BatchStopReason> stopReason = Optional.empty();

    // Freeze one AutoPagerize snapshot for the whole batch so active switches mid-batch do not
    // change rule selection for later items in this run.
    BatchAutopagerizeState batchAp = resolveBatchAutopagerizeState(targets);

    for (PendingFullTextTarget target : targets) {
      if (Thread.currentThread().isInterrupted()) {
        stopReason = Optional.of(BatchStopReason.INTERRUPTED);
        break;
      }
      try {
        TextExtractionOutcome outcome;
        if (target.method().usesAutopagerize()) {
          Optional<TextExtractionOutcome> batchFailure =
              batchAp.failureFor(target.header().fetchUrl());
          if (batchFailure.isPresent()) {
            outcome = batchFailure.get();
          } else {
            outcome = extract(target.header(), target.method(), batchAp.snapshot());
          }
        } else {
          outcome = extract(target.header(), target.method(), Optional.empty());
        }
        switch (outcome) {
          case TextExtractionOutcome.Extracted extracted -> {
            try {
              ContentFullTextWriteOutcome write =
                  contentFullTextWriter.saveIfAbsent(target.header(), target.method(), extracted);
              switch (write) {
                case INSERTED -> {
                  inserted++;
                  logExtractionSuccess(target, extracted);
                }
                case ALREADY_EXISTS -> alreadyPresent++;
                case NO_CONTENT -> noContent++;
              }
            } catch (RuntimeException e) {
              failed++;
              logger.error(
                  "Failed to persist full text for {} stage={} kind={} message={}",
                  target.header().fetchUrl(),
                  FailureStage.PERSIST_FULL_TEXT,
                  FailureKind.PERSISTENCE,
                  e.getMessage(),
                  e);
            }
          }
          case TextExtractionOutcome.NoContent ignored -> {
            noContent++;
            logger.debug("No full text content for {}", target.header().fetchUrl());
          }
          case TextExtractionOutcome.Skipped skip -> {
            skipped++;
            logger.warn(
                "Skipping full text extraction for {} reason={}",
                target.header().fetchUrl(),
                skip.reason());
          }
          case TextExtractionOutcome.Failed failure -> {
            failed++;
            OperationFailure op = failure.failure();
            if (op.cause().isPresent()) {
              logger.error(
                  "Failed to extract full text for {} stage={} kind={} message={}",
                  op.subject(),
                  op.stage(),
                  op.kind(),
                  op.message(),
                  op.cause().get());
            } else {
              logger.error(
                  "Failed to extract full text for {} stage={} kind={} message={}",
                  op.subject(),
                  op.stage(),
                  op.kind(),
                  op.message());
            }
            if (op.interrupted()) {
              stopReason = Optional.of(BatchStopReason.INTERRUPTED);
              return new FullTextExtractionBatchResult(
                  targets.size(), inserted, alreadyPresent, noContent, skipped, failed, stopReason);
            }
          }
        }
      } catch (RuntimeException e) {
        failed++;
        logger.error(
            "Unexpected failure extracting full text for {}", target.header().fetchUrl(), e);
        if (Thread.currentThread().isInterrupted()) {
          stopReason = Optional.of(BatchStopReason.INTERRUPTED);
          return new FullTextExtractionBatchResult(
              targets.size(), inserted, alreadyPresent, noContent, skipped, failed, stopReason);
        }
      }
    }

    return new FullTextExtractionBatchResult(
        targets.size(), inserted, alreadyPresent, noContent, skipped, failed, stopReason);
  }

  public TextExtractionOutcome extract(ContentHeader header) {
    return extract(header, FullTextMethod.defaultMethod());
  }

  public TextExtractionOutcome extract(ContentHeader header, FullTextMethod method) {
    return extract(header, method, Optional.empty());
  }

  /**
   * @param batchSnapshot when present and method uses AutoPagerize, use this immutable snapshot
   *     instead of loading active again. Empty means load active (single-article / probe path).
   */
  public TextExtractionOutcome extract(
      ContentHeader header,
      FullTextMethod method,
      Optional<AutoPagerizeRuleSnapshot> batchSnapshot) {
    return switch (method.definition()) {
      case Definition.FeedEntry ignored -> extractFromFeed(header);
      case Definition.HttpArticle http -> extractFromHttp(header, http, batchSnapshot);
      case Definition.PlaywrightArticle playwright ->
          extractFromPlaywright(header, playwright, batchSnapshot);
    };
  }

  private void logExtractionSuccess(
      PendingFullTextTarget target, TextExtractionOutcome.Extracted extracted) {
    if (extracted.pagination().isPresent()) {
      PaginationMetadata meta = extracted.pagination().get();
      logger.info(
          "Persisted full text for {} method={} source={} pages={} stop={} dataset={}",
          target.header().fetchUrl(),
          target.method().value(),
          extracted.decision().source().wireValue(),
          meta.pageCount(),
          meta.stopReason(),
          meta.datasetId());
    } else {
      logger.debug(
          "Persisted full text for {} method={} source={}",
          target.header().fetchUrl(),
          target.method().value(),
          extracted.decision().source().wireValue());
    }
  }

  private BatchAutopagerizeState resolveBatchAutopagerizeState(
      List<PendingFullTextTarget> targets) {
    boolean needsAutopagerize =
        targets.stream().anyMatch(target -> target.method().usesAutopagerize());
    if (!needsAutopagerize) {
      return BatchAutopagerizeState.notNeeded();
    }
    try {
      return BatchAutopagerizeState.loaded(autoPagerizeRuleCatalog.getActiveSnapshot());
    } catch (AutoPagerizeCatalogException e) {
      logger.error(
          "Failed to load batch AutoPagerize snapshot stage={} kind={} message={}",
          FailureStage.MATCH_AUTOPAGERIZE_RULE,
          FailureKind.UNEXPECTED,
          e.getMessage(),
          e);
      return BatchAutopagerizeState.failed(
          OperationFailure.of(
              FailureStage.MATCH_AUTOPAGERIZE_RULE,
              FailureKind.UNEXPECTED,
              "batch",
              "Failed to load or compile active AutoPagerize rules: " + e.getMessage(),
              e));
    } catch (RuntimeException e) {
      logger.error(
          "Failed to load batch AutoPagerize snapshot stage={} kind={} message={}",
          FailureStage.LOAD_AUTOPAGERIZE_DATABASE,
          FailureKind.UNEXPECTED,
          e.getMessage(),
          e);
      return BatchAutopagerizeState.failed(
          OperationFailure.of(
              FailureStage.LOAD_AUTOPAGERIZE_DATABASE,
              FailureKind.UNEXPECTED,
              "batch",
              "Failed to load active AutoPagerize dataset: " + e.getMessage(),
              e));
    }
  }

  private TextExtractionOutcome extractFromFeed(ContentHeader header) {
    String feedText = header.feedText();
    if (feedText == null || feedText.isBlank()) {
      return new TextExtractionOutcome.NoContent(
          NoContentReason.FEED_CONTENT_MISSING, ExtractionDecision.of(ExtractionSource.FEED));
    }
    String text = Jsoup.parse(feedText).text();
    if (text == null || text.isBlank()) {
      return new TextExtractionOutcome.NoContent(
          NoContentReason.FEED_CONTENT_MISSING, ExtractionDecision.of(ExtractionSource.FEED));
    }
    return new TextExtractionOutcome.Extracted(
        text,
        ExtractionDecision.of(ExtractionSource.FEED),
        Optional.empty(),
        Optional.of(header.canonicalUrl()));
  }

  private TextExtractionOutcome extractFromHttp(
      ContentHeader header,
      Definition.HttpArticle http,
      Optional<AutoPagerizeRuleSnapshot> batchSnapshot) {
    if (http.pagination() == PaginationMode.AUTOPAGERIZE) {
      return extractFromHttpAutopagerize(header, http.extractor(), batchSnapshot);
    }
    return extractFromHttpSinglePage(header, http.extractor());
  }

  private TextExtractionOutcome extractFromHttpSinglePage(
      ContentHeader header, HtmlExtractor extractor) {
    try {
      HttpFetchService.FetchedResource resource =
          httpFetchService.get(URI.create(header.fetchUrl()));
      TextExtractionOutcome outcome =
          htmlTextExtractor.extract(resource.uri().toString(), resource.body(), extractor);
      return attachExtractedUrl(outcome, resource.uri().toString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new TextExtractionOutcome.Failed(
          OperationFailure.of(
              FailureStage.FETCH_ARTICLE,
              FailureKind.INTERRUPTED,
              header.fetchUrl(),
              "Article fetch interrupted for " + header.fetchUrl(),
              e));
    } catch (HttpStatusException e) {
      return new TextExtractionOutcome.Failed(
          OperationFailure.of(
              FailureStage.FETCH_ARTICLE,
              FailureKind.HTTP_STATUS,
              header.fetchUrl(),
              "Article fetch failed for " + header.fetchUrl() + ": " + e.getMessage(),
              e));
    } catch (IOException e) {
      return new TextExtractionOutcome.Failed(
          OperationFailure.of(
              FailureStage.FETCH_ARTICLE,
              FailureKind.IO,
              header.fetchUrl(),
              "Article fetch failed for " + header.fetchUrl() + ": " + e.getMessage(),
              e));
    }
  }

  /**
   * HTTP AutoPagerize path: use the batch-frozen snapshot when provided, otherwise load active.
   * Failures never produce partial success text.
   */
  private TextExtractionOutcome extractFromHttpAutopagerize(
      ContentHeader header,
      HtmlExtractor extractor,
      Optional<AutoPagerizeRuleSnapshot> batchSnapshot) {
    SnapshotResolution snapshotResolution = resolveSnapshot(header.fetchUrl(), batchSnapshot);
    if (snapshotResolution instanceof SnapshotResolution.Failed failed) {
      return new TextExtractionOutcome.Failed(failed.failure());
    }
    AutoPagerizeRuleSnapshot snapshot = ((SnapshotResolution.Ready) snapshotResolution).snapshot();

    URI startUri;
    try {
      startUri = URI.create(header.fetchUrl());
    } catch (IllegalArgumentException e) {
      return new TextExtractionOutcome.Failed(
          OperationFailure.of(
              FailureStage.FETCH_ARTICLE_PAGE,
              FailureKind.INVALID_INPUT,
              header.fetchUrl(),
              "Invalid article fetch URL: " + header.fetchUrl(),
              e));
    }

    try (ArticlePageSession session = httpArticlePageSessionFactory.open()) {
      PaginationResult pagination;
      try {
        pagination =
            autoPagerizeEngine.paginate(
                startUri, session, snapshot, properties.autopagerize().toPaginationPolicy());
      } catch (RuntimeException e) {
        return failedOutcome(
            header,
            FailureStage.MATCH_AUTOPAGERIZE_RULE,
            FailureKind.UNEXPECTED,
            "HTTP AutoPagerize rule matching failed for " + header.fetchUrl(),
            e);
      }
      return toPaginationTextOutcome(pagination, snapshot, extractor, Optional.empty(), false);
    } catch (RuntimeException e) {
      return new TextExtractionOutcome.Failed(
          OperationFailure.of(
              FailureStage.FETCH_ARTICLE_PAGE,
              FailureKind.UNEXPECTED,
              header.fetchUrl(),
              "HTTP AutoPagerize extraction failed for "
                  + header.fetchUrl()
                  + ": "
                  + e.getMessage(),
              e));
    }
  }

  /**
   * Shared AutoPagerize pagination → text extraction used by production HTTP/Playwright paths and
   * probe (via the same helpers). Partial text is never returned as success.
   */
  TextExtractionOutcome toPaginationTextOutcome(
      PaginationResult pagination,
      AutoPagerizeRuleSnapshot snapshot,
      HtmlExtractor extractor,
      Optional<String> xpathOverride,
      boolean explicitDatasetSelection) {
    if (pagination instanceof PaginationResult.Failed failed) {
      return new TextExtractionOutcome.Failed(failed.failure())
          .withPagination(
              PaginationMetadataFactory.fromFailed(failed, snapshot, explicitDatasetSelection));
    }
    try {
      return toPaginatedTextOutcome(
          (PaginationResult.Succeeded) pagination,
          snapshot,
          extractor,
          xpathOverride,
          explicitDatasetSelection);
    } catch (RuntimeException e) {
      return new TextExtractionOutcome.Failed(
          OperationFailure.of(
              FailureStage.EXTRACT_TEXT,
              FailureKind.EXTRACTION,
              snapshot.datasetId() + "",
              "AutoPagerize text extraction failed: " + e.getMessage(),
              e));
    }
  }

  private static TextExtractionOutcome.Failed failedOutcome(
      ContentHeader header, FailureStage stage, FailureKind kind, String message, Throwable cause) {
    return new TextExtractionOutcome.Failed(
        OperationFailure.of(
            stage, kind, header.fetchUrl(), message + ": " + cause.getMessage(), cause));
  }

  private TextExtractionOutcome toPaginatedTextOutcome(
      PaginationResult.Succeeded pagination,
      AutoPagerizeRuleSnapshot snapshot,
      HtmlExtractor extractor,
      Optional<String> xpathOverride,
      boolean explicitDatasetSelection) {
    PaginatedExtractionResult result =
        paginatedHtmlTextExtractor.extract(pagination, extractor, xpathOverride);
    PaginationMetadata metadata =
        PaginationMetadataFactory.fromSucceeded(
            pagination, snapshot, result.contributions(), explicitDatasetSelection);
    String firstFinalUrl = pagination.firstPage().finalUri().toString();
    return switch (result.outcome()) {
      case TextExtractionOutcome.Extracted extracted ->
          extracted.withPagination(metadata).withExtractedUrl(firstFinalUrl);
      case TextExtractionOutcome.NoContent noContent -> noContent;
      case TextExtractionOutcome.Skipped skipped -> skipped;
      case TextExtractionOutcome.Failed failed -> failed;
    };
  }

  private SnapshotResolution resolveSnapshot(
      String subject, Optional<AutoPagerizeRuleSnapshot> batchSnapshot) {
    if (batchSnapshot.isPresent()) {
      return new SnapshotResolution.Ready(batchSnapshot.get());
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
      return new SnapshotResolution.Ready(active.get());
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

  private TextExtractionOutcome extractFromPlaywright(
      ContentHeader header,
      Definition.PlaywrightArticle definition,
      Optional<AutoPagerizeRuleSnapshot> batchSnapshot) {
    if (!properties.playwright().enabled()) {
      return new TextExtractionOutcome.Skipped(TextExtractionSkipReason.PLAYWRIGHT_DISABLED);
    }
    if (definition.pagination() == PaginationMode.AUTOPAGERIZE) {
      return extractFromPlaywrightAutopagerize(header, definition.extractor(), batchSnapshot);
    }
    return extractFromPlaywrightSinglePage(header, definition);
  }

  private TextExtractionOutcome extractFromPlaywrightSinglePage(
      ContentHeader header, Definition.PlaywrightArticle definition) {
    try {
      URI requestedUri = URI.create(header.fetchUrl());
      // Keep header.fetchUrl() for extract-rule matching (unchanged semantics).
      String html = playwrightHtmlSource.render(requestedUri, definition.mode());
      TextExtractionOutcome outcome =
          htmlTextExtractor.extract(header.fetchUrl(), html, definition.extractor());
      return attachExtractedUrl(outcome, header.fetchUrl());
    } catch (RuntimeException e) {
      return new TextExtractionOutcome.Failed(
          OperationFailure.of(
              FailureStage.RENDER_ARTICLE,
              FailureKind.RENDER,
              header.fetchUrl(),
              "Playwright render failed for " + header.fetchUrl() + ": " + e.getMessage(),
              e));
    }
  }

  /**
   * Playwright AutoPagerize path: use the batch-frozen snapshot when provided, otherwise load
   * active. Failures never produce partial success text. No Infy extension is used.
   */
  private TextExtractionOutcome extractFromPlaywrightAutopagerize(
      ContentHeader header,
      HtmlExtractor extractor,
      Optional<AutoPagerizeRuleSnapshot> batchSnapshot) {
    SnapshotResolution snapshotResolution = resolveSnapshot(header.fetchUrl(), batchSnapshot);
    if (snapshotResolution instanceof SnapshotResolution.Failed failed) {
      return new TextExtractionOutcome.Failed(failed.failure());
    }
    AutoPagerizeRuleSnapshot snapshot = ((SnapshotResolution.Ready) snapshotResolution).snapshot();

    URI startUri;
    try {
      startUri = URI.create(header.fetchUrl());
    } catch (IllegalArgumentException e) {
      return new TextExtractionOutcome.Failed(
          OperationFailure.of(
              FailureStage.FETCH_ARTICLE_PAGE,
              FailureKind.INVALID_INPUT,
              header.fetchUrl(),
              "Invalid article fetch URL: " + header.fetchUrl(),
              e));
    }

    try {
      return playwrightHtmlSource.withStandardSession(
          session -> {
            PaginationResult pagination;
            try {
              pagination =
                  autoPagerizeEngine.paginate(
                      startUri, session, snapshot, properties.autopagerize().toPaginationPolicy());
            } catch (RuntimeException e) {
              throw new PlaywrightSessionFailure(
                  OperationFailure.of(
                      FailureStage.MATCH_AUTOPAGERIZE_RULE,
                      FailureKind.UNEXPECTED,
                      header.fetchUrl(),
                      "Playwright AutoPagerize rule matching failed for " + header.fetchUrl(),
                      e));
            }
            // Return Failed (with optional pagination diagnostics) without wrapping so metadata is
            // not lost; session cleanup still runs after the work lambda returns.
            return toPaginationTextOutcome(
                pagination, snapshot, extractor, Optional.empty(), false);
          });
    } catch (PlaywrightSessionFailure e) {
      return new TextExtractionOutcome.Failed(e.failure());
    } catch (RuntimeException e) {
      return new TextExtractionOutcome.Failed(
          OperationFailure.of(
              FailureStage.RENDER_ARTICLE,
              FailureKind.RENDER,
              header.fetchUrl(),
              "Playwright AutoPagerize extraction failed for "
                  + header.fetchUrl()
                  + ": "
                  + e.getMessage(),
              e));
    }
  }

  private static TextExtractionOutcome attachExtractedUrl(
      TextExtractionOutcome outcome, String url) {
    if (outcome instanceof TextExtractionOutcome.Extracted extracted) {
      return extracted.withExtractedUrl(url);
    }
    return outcome;
  }

  private sealed interface SnapshotResolution
      permits SnapshotResolution.Ready, SnapshotResolution.Failed {
    record Ready(AutoPagerizeRuleSnapshot snapshot) implements SnapshotResolution {
      public Ready {
        java.util.Objects.requireNonNull(snapshot, "snapshot must not be null");
      }
    }

    record Failed(OperationFailure failure) implements SnapshotResolution {
      public Failed {
        java.util.Objects.requireNonNull(failure, "failure must not be null");
      }
    }
  }

  /**
   * Batch-scoped AutoPagerize snapshot. Catalog load is skipped when no target needs AutoPagerize.
   */
  private record BatchAutopagerizeState(
      Optional<AutoPagerizeRuleSnapshot> snapshot, Optional<OperationFailure> loadFailure) {

    static BatchAutopagerizeState notNeeded() {
      return new BatchAutopagerizeState(Optional.empty(), Optional.empty());
    }

    static BatchAutopagerizeState loaded(Optional<AutoPagerizeRuleSnapshot> active) {
      return new BatchAutopagerizeState(active, Optional.empty());
    }

    static BatchAutopagerizeState failed(OperationFailure failure) {
      return new BatchAutopagerizeState(Optional.empty(), Optional.of(failure));
    }

    Optional<TextExtractionOutcome> failureFor(String subject) {
      if (loadFailure.isPresent()) {
        OperationFailure failure = loadFailure.get();
        return Optional.of(
            new TextExtractionOutcome.Failed(
                new OperationFailure(
                    failure.stage(), failure.kind(), subject, failure.message(), failure.cause())));
      }
      if (snapshot.isEmpty()) {
        return Optional.of(
            new TextExtractionOutcome.Failed(
                OperationFailure.of(
                    FailureStage.LOAD_AUTOPAGERIZE_DATABASE,
                    FailureKind.INVALID_INPUT,
                    subject,
                    "No active AutoPagerize dataset; import and activate a local SITEINFO JSON"
                        + " first")));
      }
      return Optional.empty();
    }
  }
}
