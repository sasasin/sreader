package net.sasasin.sreader.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

@Service
public class FeedDiscoveryService {

  private final PlaywrightHtmlSource playwrightHtmlSource;

  public FeedDiscoveryService(PlaywrightHtmlSource playwrightHtmlSource) {
    this.playwrightHtmlSource = playwrightHtmlSource;
  }

  public List<URI> discover(URI siteUrl) {
    RenderedPage rendered = playwrightHtmlSource.renderPage(siteUrl.toString(), false);
    String base = rendered.finalUri() != null ? rendered.finalUri().toString() : siteUrl.toString();
    Document doc = Jsoup.parse(rendered.html(), base);

    Elements links = doc.select("link[rel~=(?i)\\balternate\\b][href]");

    Set<String> seen = new LinkedHashSet<>();
    List<URI> results = new ArrayList<>();

    for (Element link : links) {
      String type = link.attr("type");
      if (!isFeedType(type)) {
        continue;
      }
      String href = link.absUrl("href");
      if (href.isBlank()) {
        href = link.attr("href");
        if (href.isBlank()) {
          continue;
        }
        // absUrl may fail if no base, fallback
        try {
          href = siteUrl.resolve(href).toString();
        } catch (Exception ignored) {
          continue;
        }
      }
      if (seen.add(href)) {
        try {
          results.add(URI.create(href));
        } catch (IllegalArgumentException ignored) {
          // skip bad
        }
      }
    }
    return results;
  }

  private boolean isFeedType(String type) {
    if (type == null || type.isBlank()) {
      return false;
    }
    String t = type.toLowerCase(Locale.ROOT).trim();
    return t.contains("rss")
        || t.contains("atom")
        || t.equals("application/feed+json")
        || t.equals("application/xml")
        || t.equals("text/xml");
  }
}
