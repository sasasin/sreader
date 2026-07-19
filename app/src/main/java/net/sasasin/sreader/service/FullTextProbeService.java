package net.sasasin.sreader.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import java.net.URI;
import java.util.Optional;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.domain.ExtractionPlan;
import net.sasasin.sreader.domain.FeedEntrySelection;
import net.sasasin.sreader.domain.FullTextMethod;
import net.sasasin.sreader.domain.ProbeResult;
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
  private final FeedReaderProperties properties;

  public FullTextProbeService(
      HttpFetchService httpFetchService,
      PlaywrightHtmlSource playwrightHtmlSource,
      HtmlTextExtractor htmlTextExtractor,
      FeedDocumentService feedDocumentService,
      FeedEntryPicker feedEntryPicker,
      FeedEntryFullTextExtractor feedEntryFullTextExtractor,
      FeedReaderProperties properties) {
    this.httpFetchService = httpFetchService;
    this.documentFetcher = new ProbeDocumentFetcher(httpFetchService, playwrightHtmlSource);
    this.htmlTextExtractor = htmlTextExtractor;
    this.feedDocumentService = feedDocumentService;
    this.feedEntryPicker = feedEntryPicker;
    this.feedEntryFullTextExtractor = feedEntryFullTextExtractor;
    this.properties = properties;
  }

  public ProbeResult probeArticle(
      URI articleUrl, FullTextMethod method, Optional<String> xpathOverride) {
    if (method == FullTextMethod.FEED) {
      throw new IllegalArgumentException("--method feed is not supported for probe article");
    }
    ExtractionPlan plan = ExtractionPlan.from(method);
    checkPlaywrightEnabled(plan);

    ProbeDocumentFetcher.FetchedProbeDocument document =
        documentFetcher.fetch(articleUrl, plan, articleUrl.toString());
    String text =
        htmlTextExtractor.extract(
            document.finalUri().toString(), document.html(), plan.extractorKind(), xpathOverride);
    return new ProbeResult(
        articleUrl,
        document.finalUri(),
        extractTitleFromHtml(document.html()),
        method,
        text == null ? "" : text);
  }

  public ProbeResult probeFeed(
      URI feedUrl,
      FullTextMethod method,
      FeedEntrySelection selection,
      Optional<String> xpathOverride) {
    SyndFeed syndFeed = feedDocumentService.fetch(feedUrl);
    boolean requireEntryLink = method != FullTextMethod.FEED;
    Optional<SyndEntry> picked = feedEntryPicker.pick(syndFeed, selection, requireEntryLink);
    if (picked.isEmpty()) {
      throw new NoMatchingEntryException("No feed entry matched selection for " + feedUrl);
    }
    SyndEntry entry = picked.get();

    URI entryLink = null;
    if (method != FullTextMethod.FEED && entry.getLink() != null && !entry.getLink().isBlank()) {
      try {
        entryLink = httpFetchService.resolveRedirect(URI.create(entry.getLink()));
      } catch (Exception ignored) {
        entryLink = URI.create(entry.getLink());
      }
    }

    String entryTitle = entry.getTitle();

    if (method == FullTextMethod.FEED) {
      if (xpathOverride.isPresent()) {
        throw new IllegalArgumentException("--xpath is not applicable for --method feed");
      }
      String text = feedEntryFullTextExtractor.extract(entry).orElse("");
      URI finalForResult = entryLink != null ? entryLink : feedUrl;
      return new ProbeResult(feedUrl, finalForResult, entryTitle, method, text);
    }

    // non-feed: delegate to article logic using entry's link
    if (entryLink == null) {
      throw new NoMatchingEntryException("Selected entry has no link for " + feedUrl);
    }
    ExtractionPlan plan = ExtractionPlan.from(method);
    checkPlaywrightEnabled(plan);
    ProbeDocumentFetcher.FetchedProbeDocument document =
        documentFetcher.fetch(entryLink, plan, "entry " + entryLink);
    String text =
        htmlTextExtractor.extract(
            document.finalUri().toString(), document.html(), plan.extractorKind(), xpathOverride);
    String title = entryTitle != null ? entryTitle : extractTitleFromHtml(document.html());
    return new ProbeResult(feedUrl, document.finalUri(), title, method, text == null ? "" : text);
  }

  private void checkPlaywrightEnabled(ExtractionPlan plan) {
    if (plan.sourceKind() == ExtractionPlan.SourceKind.PLAYWRIGHT
        && !properties.playwright().enabled()) {
      throw new PlaywrightDisabledException(
          "Playwright is required for method but is disabled or misconfigured");
    }
  }

  private String extractTitleFromHtml(String html) {
    try {
      Document d = Jsoup.parse(html);
      String t = d.title();
      return (t != null && !t.isBlank()) ? t : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  // public for CLI command to catch and map to specific exit codes
  public static class NoMatchingEntryException extends RuntimeException {

    public NoMatchingEntryException(String msg) {
      super(msg);
    }
  }

  public static class PlaywrightDisabledException extends RuntimeException {

    public PlaywrightDisabledException(String msg) {
      super(msg);
    }
  }
}
