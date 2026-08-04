package net.sasasin.sreader.service.extraction.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import java.net.URI;
import java.util.Objects;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.service.autopagerize.ArticlePageSession;
import net.sasasin.sreader.service.autopagerize.PageLoadException;
import net.sasasin.sreader.service.autopagerize.PageSnapshot;
import net.sasasin.sreader.service.outcome.FailureKind;

/**
 * Short-lived standard Playwright session for one article pagination chain.
 *
 * <p>One {@link BrowserContext} and one {@link Page} are reused for every {@link #load(URI)} in the
 * chain. No extension, persistent profile, or user-data directory is used. {@code byteSize} is the
 * UTF-8 byte length of captured HTML (raw response bytes are not available from Playwright).
 *
 * <p>Callers must serialize access through {@link PlaywrightHtmlSource}; this type is not
 * thread-safe on its own.
 */
final class PlaywrightArticlePageSession implements ArticlePageSession {

  private final BrowserContext context;
  private final Page page;
  private final PlaywrightPageNavigator navigator;
  private boolean closeRequested;
  private boolean pageClosed;
  private boolean contextClosed;

  PlaywrightArticlePageSession(
      BrowserContext context, Page page, PlaywrightPageNavigator navigator) {
    this.context = Objects.requireNonNull(context, "context must not be null");
    this.page = Objects.requireNonNull(page, "page must not be null");
    this.navigator = Objects.requireNonNull(navigator, "navigator must not be null");
  }

  /**
   * Opens a viewport-matched context and a single page from the shared regular browser. On page
   * creation failure the context is closed before rethrowing.
   */
  static PlaywrightArticlePageSession open(
      PlaywrightRuntime runtime,
      FeedReaderProperties.Playwright settings,
      PlaywrightPageNavigator navigator) {
    Objects.requireNonNull(runtime, "runtime must not be null");
    Objects.requireNonNull(settings, "settings must not be null");
    Objects.requireNonNull(navigator, "navigator must not be null");
    BrowserContext context =
        runtime
            .browser()
            .newContext(
                new Browser.NewContextOptions()
                    .setViewportSize(settings.viewportWidth(), settings.viewportHeight()));
    RuntimeException primary = null;
    try {
      Page page = context.newPage();
      return new PlaywrightArticlePageSession(context, page, navigator);
    } catch (RuntimeException e) {
      primary = e;
    }
    primary = PlaywrightCloseSupport.close(primary, context::close);
    throw primary;
  }

  @Override
  public PageSnapshot load(URI uri) throws PageLoadException {
    Objects.requireNonNull(uri, "uri must not be null");
    if (closeRequested) {
      throw new PageLoadException(
          FailureKind.INVALID_INPUT, "Playwright article page session is closed");
    }
    try {
      navigator.navigate(page, uri);
      navigator.waitNetworkIdleBestEffort(page);
      RenderedPage rendered = navigator.capture(page, uri);
      // Playwright exposes rendered DOM HTML, not raw HTTP body bytes; UTF-8 length is the size
      // used for AutoPagerize max-page / max-total policy checks.
      return PageSnapshot.ofUtf8(uri, rendered.finalUri(), rendered.html());
    } catch (RuntimeException e) {
      if (isInterrupted(e)) {
        Thread.currentThread().interrupt();
        throw new PageLoadException(
            FailureKind.INTERRUPTED, "Playwright page load interrupted for " + uri, e);
      }
      throw new PageLoadException(
          FailureKind.RENDER, "Playwright page load failed for " + uri + ": " + message(e), e);
    }
  }

  @Override
  public void close() {
    if (contextClosed) {
      return;
    }
    closeRequested = true;
    RuntimeException primary = null;
    if (!pageClosed) {
      try {
        page.close();
        pageClosed = true;
      } catch (RuntimeException e) {
        primary = e;
      }
    }
    if (!contextClosed) {
      try {
        context.close();
        contextClosed = true;
        pageClosed = true;
      } catch (RuntimeException e) {
        primary =
            PlaywrightCloseSupport.close(
                primary,
                () -> {
                  throw e;
                });
      }
    }
    PlaywrightCloseSupport.throwIfPresent(primary);
  }

  private static boolean isInterrupted(Throwable error) {
    if (Thread.currentThread().isInterrupted()) {
      return true;
    }
    for (Throwable current = error; current != null; current = current.getCause()) {
      if (current instanceof InterruptedException) {
        return true;
      }
    }
    return false;
  }

  private static String message(Throwable e) {
    String message = e.getMessage();
    return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
  }
}
