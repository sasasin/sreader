package net.sasasin.sreader.service.extraction.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import net.sasasin.sreader.config.FeedReaderProperties;

/** Shared navigation, network-idle wait, and content capture for Playwright pages. */
final class PlaywrightPageNavigator {

  private final FeedReaderProperties.Playwright settings;

  PlaywrightPageNavigator(FeedReaderProperties.Playwright settings) {
    this.settings = Objects.requireNonNull(settings, "settings must not be null");
  }

  void navigate(Page page, URI requestedUri) {
    Objects.requireNonNull(page, "page must not be null");
    Objects.requireNonNull(requestedUri, "requestedUri must not be null");
    page.navigate(
        requestedUri.toString(),
        new Page.NavigateOptions()
            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
            .setTimeout(settings.navigationTimeout().toMillis()));
  }

  void waitNetworkIdleBestEffort(Page page) {
    Objects.requireNonNull(page, "page must not be null");
    try {
      page.waitForLoadState(
          LoadState.NETWORKIDLE,
          new Page.WaitForLoadStateOptions().setTimeout(settings.networkIdleTimeout().toMillis()));
    } catch (RuntimeException e) {
      // Long-polling pages may never become idle; rendered DOM is still usable.
    }
  }

  RenderedPage capture(Page page, URI requestedUri) {
    Objects.requireNonNull(page, "page must not be null");
    Objects.requireNonNull(requestedUri, "requestedUri must not be null");
    URI finalUri = resolveFinalUri(page.url(), requestedUri);
    return new RenderedPage(finalUri, page.content());
  }

  URI resolveFinalUri(String pageUrl, URI requestedUri) {
    Objects.requireNonNull(requestedUri, "requestedUri must not be null");
    if (pageUrl == null || pageUrl.isBlank()) {
      return requestedUri;
    }
    try {
      return new URI(pageUrl);
    } catch (URISyntaxException e) {
      return requestedUri;
    }
  }
}
