package net.sasasin.sreader.service;

import java.net.URI;
import java.util.List;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.ContentHeader;
import net.sasasin.sreader.domain.ExtractionPlan;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.PendingFullTextTarget;
import net.sasasin.sreader.repository.ContentHeaderRepository;
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
  private final HttpFetchService httpFetchService;
  private final PlaywrightHtmlSource playwrightHtmlSource;
  private final FeedReaderProperties properties;

  public FullTextExtractionService(
      ContentHeaderRepository contentHeaderRepository,
      ContentFullTextWriter contentFullTextWriter,
      HtmlTextExtractor htmlTextExtractor,
      HttpFetchService httpFetchService,
      PlaywrightHtmlSource playwrightHtmlSource,
      FeedReaderProperties properties) {
    this.contentHeaderRepository = contentHeaderRepository;
    this.contentFullTextWriter = contentFullTextWriter;
    this.htmlTextExtractor = htmlTextExtractor;
    this.httpFetchService = httpFetchService;
    this.playwrightHtmlSource = playwrightHtmlSource;
    this.properties = properties;
  }

  public int extractPending(int limit) {
    List<PendingFullTextTarget> targets =
        contentHeaderRepository.findWithoutFullTextForUrlExtraction(limit);
    int inserted = 0;
    for (PendingFullTextTarget target : targets) {
      try {
        ExtractionPlan plan = ExtractionPlan.from(target.method());
        if (plan.sourceKind() == ExtractionPlan.SourceKind.PLAYWRIGHT
            && !properties.playwright().enabled()) {
          logger.warn(
              "Skipping disabled Playwright full text method {} for {}",
              target.method().value(),
              target.header().url());
          continue;
        }
        String fullText = extract(target.header(), target.method());
        if (contentFullTextWriter.saveIfAbsent(target.header(), fullText)) {
          inserted++;
        }
      } catch (Exception e) {
        logger.warn("Failed to extract full text from {}", target.header().url(), e);
      }
    }
    return inserted;
  }

  public String extract(ContentHeader header) throws Exception {
    return extract(header, FullTextMethod.HTTP);
  }

  public String extract(ContentHeader header, FullTextMethod method) throws Exception {
    ExtractionPlan plan = ExtractionPlan.from(method);
    return switch (plan.sourceKind()) {
      case FEED -> header.feedText() == null ? "" : Jsoup.parse(header.feedText()).text();
      case HTTP -> extractFromHttp(header, plan);
      case PLAYWRIGHT -> extractFromPlaywright(header, plan);
    };
  }

  private String extractFromHttp(ContentHeader header, ExtractionPlan plan)
      throws java.io.IOException, InterruptedException {
    HttpFetchService.FetchedResource resource = httpFetchService.get(URI.create(header.url()));
    return htmlTextExtractor.extract(
        resource.uri().toString(), resource.body(), plan.extractorKind());
  }

  private String extractFromPlaywright(ContentHeader header, ExtractionPlan plan) {
    String html = playwrightHtmlSource.render(header.url(), plan.useInfyScroll());
    return htmlTextExtractor.extract(header.url(), html, plan.extractorKind());
  }
}
