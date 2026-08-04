package net.sasasin.sreader.service.extraction.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import java.net.URI;
import java.util.Objects;
import net.sasasin.sreader.config.FeedReaderProperties;

/**
 * Renders a page with a short-lived BrowserContext from the shared regular Browser. One context per
 * render.
 */
final class StandardPlaywrightPageRenderer {

  private final FeedReaderProperties.Playwright settings;
  private final PlaywrightRuntime runtime;
  private final PlaywrightPageNavigator navigator;

  StandardPlaywrightPageRenderer(
      FeedReaderProperties.Playwright settings,
      PlaywrightRuntime runtime,
      PlaywrightPageNavigator navigator) {
    this.settings = Objects.requireNonNull(settings, "settings must not be null");
    this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
    this.navigator = Objects.requireNonNull(navigator, "navigator must not be null");
  }

  RenderedPage render(URI requestedUri) {
    Objects.requireNonNull(requestedUri, "requestedUri must not be null");
    BrowserContext context =
        runtime
            .browser()
            .newContext(
                new Browser.NewContextOptions()
                    .setViewportSize(settings.viewportWidth(), settings.viewportHeight()));
    RuntimeException primary = null;
    RenderedPage result = null;
    try {
      Page page = context.newPage();
      navigator.navigate(page, requestedUri);
      navigator.waitNetworkIdleBestEffort(page);
      result = navigator.capture(page, requestedUri);
    } catch (RuntimeException e) {
      primary = e;
    } finally {
      primary = PlaywrightCloseSupport.close(primary, context::close);
    }
    if (primary != null) {
      throw primary;
    }
    return result;
  }

  /**
   * Opens one short-lived standard context/page session, runs {@code work}, and always closes the
   * session. Primary work failures are preserved when close also fails.
   *
   * <p>Must be called only while holding the {@link PlaywrightHtmlSource} monitor.
   */
  <T> T withSession(PlaywrightSessionWork<T> work) {
    Objects.requireNonNull(work, "work must not be null");
    PlaywrightArticlePageSession session =
        PlaywrightArticlePageSession.open(runtime, settings, navigator);
    RuntimeException primary = null;
    T result = null;
    try {
      result = work.apply(session);
    } catch (RuntimeException e) {
      primary = e;
    } finally {
      primary = PlaywrightCloseSupport.close(primary, session::close);
    }
    if (primary != null) {
      throw primary;
    }
    return result;
  }
}
