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
  private final PlaywrightHtmlSource playwrightHtmlSource;
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
    this.playwrightHtmlSource = playwrightHtmlSource;
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

    URI finalUrl;
    String html;
    String usedUrlForExtract;

    switch (plan.sourceKind()) {
      case HTTP -> {
        try {
          HttpFetchService.FetchedResource res = httpFetchService.get(articleUrl);
          finalUrl = res.uri();
          html = res.body();
          usedUrlForExtract = finalUrl.toString();
        } catch (Exception e) {
          if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
          }
          throw new RuntimeException("HTTP fetch failed for " + articleUrl, e);
        }
      }
      case PLAYWRIGHT -> {
        RenderedPage page =
            playwrightHtmlSource.renderPage(articleUrl.toString(), plan.useInfyScroll());
        finalUrl = page.finalUri() != null ? page.finalUri() : articleUrl;
        html = page.html();
        usedUrlForExtract = finalUrl.toString();
      }
      default ->
          throw new IllegalStateException("Unexpected source for article: " + plan.sourceKind());
    }

    String text =
        htmlTextExtractor.extract(usedUrlForExtract, html, plan.extractorKind(), xpathOverride);
    String title = extractTitleFromHtml(html);
    return new ProbeResult(articleUrl, finalUrl, title, method, text == null ? "" : text);
  }

  public ProbeResult probeFeed(
      URI feedUrl,
      FullTextMethod method,
      FeedEntrySelection selection,
      Optional<String> xpathOverride) {
    SyndFeed syndFeed = feedDocumentService.fetch(feedUrl);
    Optional<SyndEntry> picked = feedEntryPicker.pick(syndFeed, selection);
    if (picked.isEmpty()) {
      throw new NoMatchingEntryException("No feed entry matched selection for " + feedUrl);
    }
    SyndEntry entry = picked.get();

    URI entryLink = null;
    if (entry.getLink() != null && !entry.getLink().isBlank()) {
      try {
        // best effort resolve redirect for verbose final
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
    // recurse like, but to avoid stack use direct
    ExtractionPlan plan = ExtractionPlan.from(method);
    checkPlaywrightEnabled(plan);

    URI finalUrl;
    String html;
    String usedUrl;

    switch (plan.sourceKind()) {
      case HTTP -> {
        try {
          HttpFetchService.FetchedResource res = httpFetchService.get(entryLink);
          finalUrl = res.uri();
          html = res.body();
          usedUrl = finalUrl.toString();
        } catch (Exception e) {
          if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
          }
          throw new RuntimeException("HTTP fetch failed for entry " + entryLink, e);
        }
      }
      case PLAYWRIGHT -> {
        RenderedPage page =
            playwrightHtmlSource.renderPage(entryLink.toString(), plan.useInfyScroll());
        finalUrl = page.finalUri() != null ? page.finalUri() : entryLink;
        html = page.html();
        usedUrl = finalUrl.toString();
      }
      default -> throw new IllegalStateException("Unexpected for feed probe: " + plan.sourceKind());
    }

    String text = htmlTextExtractor.extract(usedUrl, html, plan.extractorKind(), xpathOverride);
    String title = entryTitle != null ? entryTitle : extractTitleFromHtml(html);
    return new ProbeResult(feedUrl, finalUrl, title, method, text == null ? "" : text);
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
