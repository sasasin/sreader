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
import net.sasasin.sreader.service.autopagerize.CompiledAutoPagerizeRule;
import net.sasasin.sreader.service.autopagerize.PaginationResult;
import net.sasasin.sreader.service.extraction.browser.PlaywrightHtmlSource;
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

    for (PendingFullTextTarget target : targets) {
      if (Thread.currentThread().isInterrupted()) {
        stopReason = Optional.of(BatchStopReason.INTERRUPTED);
        break;
      }
      try {
        TextExtractionOutcome outcome = extract(target.header(), target.method());
        switch (outcome) {
          case TextExtractionOutcome.Extracted extracted -> {
            try {
              ContentFullTextWriteOutcome write =
                  contentFullTextWriter.saveIfAbsent(target.header(), extracted.text());
              switch (write) {
                case INSERTED -> inserted++;
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
    return switch (method.definition()) {
      case Definition.FeedEntry ignored -> extractFromFeed(header);
      case Definition.HttpArticle http -> extractFromHttp(header, http);
      case Definition.PlaywrightArticle playwright -> extractFromPlaywright(header, playwright);
    };
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
    return new TextExtractionOutcome.Extracted(text, ExtractionDecision.of(ExtractionSource.FEED));
  }

  private TextExtractionOutcome extractFromHttp(ContentHeader header, Definition.HttpArticle http) {
    if (http.pagination() == PaginationMode.AUTOPAGERIZE) {
      return extractFromHttpAutopagerize(header, http.extractor());
    }
    return extractFromHttpSinglePage(header, http.extractor());
  }

  private TextExtractionOutcome extractFromHttpSinglePage(
      ContentHeader header, HtmlExtractor extractor) {
    try {
      HttpFetchService.FetchedResource resource =
          httpFetchService.get(URI.create(header.fetchUrl()));
      return htmlTextExtractor.extract(resource.uri().toString(), resource.body(), extractor);
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
   * HTTP AutoPagerize path: load active rule snapshot once per article, paginate in a cookie
   * isolated session, then extract text. Failures never produce partial success text.
   */
  private TextExtractionOutcome extractFromHttpAutopagerize(
      ContentHeader header, HtmlExtractor extractor) {
    final AutoPagerizeRuleSnapshot snapshot;
    try {
      Optional<AutoPagerizeRuleSnapshot> active = autoPagerizeRuleCatalog.getActiveSnapshot();
      if (active.isEmpty()) {
        return new TextExtractionOutcome.Failed(
            OperationFailure.of(
                FailureStage.LOAD_AUTOPAGERIZE_DATABASE,
                FailureKind.INVALID_INPUT,
                header.fetchUrl(),
                "No active AutoPagerize dataset; import and activate a local SITEINFO JSON first"));
      }
      snapshot = active.get();
    } catch (AutoPagerizeCatalogException e) {
      return new TextExtractionOutcome.Failed(
          OperationFailure.of(
              FailureStage.LOAD_AUTOPAGERIZE_DATABASE,
              FailureKind.UNEXPECTED,
              header.fetchUrl(),
              "Failed to load active AutoPagerize dataset: " + e.getMessage(),
              e));
    } catch (RuntimeException e) {
      return new TextExtractionOutcome.Failed(
          OperationFailure.of(
              FailureStage.LOAD_AUTOPAGERIZE_DATABASE,
              FailureKind.UNEXPECTED,
              header.fetchUrl(),
              "Failed to load active AutoPagerize dataset: " + e.getMessage(),
              e));
    }

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
      PaginationResult pagination =
          autoPagerizeEngine.paginate(
              startUri, session, snapshot, properties.autopagerize().toPaginationPolicy());
      return switch (pagination) {
        case PaginationResult.Failed failed -> new TextExtractionOutcome.Failed(failed.failure());
        case PaginationResult.Succeeded succeeded ->
            toPaginatedTextOutcome(succeeded, snapshot, extractor);
      };
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

  private TextExtractionOutcome toPaginatedTextOutcome(
      PaginationResult.Succeeded pagination,
      AutoPagerizeRuleSnapshot snapshot,
      HtmlExtractor extractor) {
    PaginatedExtractionResult result =
        paginatedHtmlTextExtractor.extract(pagination, extractor, Optional.empty());
    PaginationMetadata metadata = paginationMetadata(pagination, snapshot, result.contributions());
    return switch (result.outcome()) {
      case TextExtractionOutcome.Extracted extracted -> extracted.withPagination(metadata);
      case TextExtractionOutcome.NoContent noContent -> noContent;
      case TextExtractionOutcome.Skipped skipped -> skipped;
      case TextExtractionOutcome.Failed failed -> failed;
    };
  }

  private static PaginationMetadata paginationMetadata(
      PaginationResult.Succeeded pagination,
      AutoPagerizeRuleSnapshot snapshot,
      List<PageTextContribution> contributions) {
    Optional<CompiledAutoPagerizeRule> rule = pagination.matchedRule();
    return PaginationMetadata.of(
        snapshot.datasetId(),
        rule.map(CompiledAutoPagerizeRule::ordinal),
        rule.map(CompiledAutoPagerizeRule::name),
        pagination.pages().size(),
        pagination.stopReason(),
        true,
        contributions);
  }

  private TextExtractionOutcome extractFromPlaywright(
      ContentHeader header, Definition.PlaywrightArticle definition) {
    if (!properties.playwright().enabled()) {
      return new TextExtractionOutcome.Skipped(TextExtractionSkipReason.PLAYWRIGHT_DISABLED);
    }
    try {
      URI requestedUri = URI.create(header.fetchUrl());
      // Keep header.fetchUrl() for extract-rule matching (unchanged semantics).
      String html = playwrightHtmlSource.render(requestedUri, definition.mode());
      return htmlTextExtractor.extract(header.fetchUrl(), html, definition.extractor());
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
}
