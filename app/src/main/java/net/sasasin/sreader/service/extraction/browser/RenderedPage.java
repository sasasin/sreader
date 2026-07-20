package net.sasasin.sreader.service.extraction.browser;

import java.net.URI;
import java.util.Objects;

/** Snapshot of a Playwright-rendered page. Both fields are non-null. */
public record RenderedPage(URI finalUri, String html) {

  public RenderedPage {
    Objects.requireNonNull(finalUri, "finalUri must not be null");
    Objects.requireNonNull(html, "html must not be null");
  }
}
