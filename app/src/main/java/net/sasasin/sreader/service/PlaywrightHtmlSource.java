package net.sasasin.sreader.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.sasasin.sreader.config.FeedReaderProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

@Service
public class PlaywrightHtmlSource implements SmartLifecycle {

  private final FeedReaderProperties properties;
  private final Supplier<Playwright> playwrightFactory;

  private Playwright playwright;
  private Browser browser;
  private BrowserContext infyContext;
  private volatile boolean running;

  @Autowired
  public PlaywrightHtmlSource(FeedReaderProperties properties) {
    this(properties, Playwright::create);
  }

  PlaywrightHtmlSource(FeedReaderProperties properties, Supplier<Playwright> playwrightFactory) {
    this.properties = properties;
    this.playwrightFactory = playwrightFactory;
  }

  public synchronized String render(String url, boolean useInfyScroll) {
    return renderPage(url, useInfyScroll).html();
  }

  public synchronized RenderedPage renderPage(String url, boolean useInfyScroll) {
    if (!properties.playwright().enabled()) {
      throw new IllegalStateException("Playwright full text extraction is disabled");
    }
    ensureStarted();
    if (useInfyScroll) {
      return renderWithInfy(url);
    }
    return renderWithoutInfy(url);
  }

  private RenderedPage renderWithoutInfy(String url) {
    BrowserContext context =
        browser.newContext(
            new Browser.NewContextOptions()
                .setViewportSize(
                    properties.playwright().viewportWidth(),
                    properties.playwright().viewportHeight()));
    Page page = context.newPage();
    try {
      page.navigate(
          url,
          new Page.NavigateOptions()
              .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
              .setTimeout(properties.playwright().navigationTimeout().toMillis()));
      waitNetworkIdleBestEffort(page);
      URI finalUri = safeUri(page.url(), url);
      return new RenderedPage(finalUri, page.content());
    } finally {
      context.close();
    }
  }

  private RenderedPage renderWithInfy(String url) {
    BrowserContext context = ensureInfyContext();
    Page page = context.newPage();
    try {
      page.navigate(
          url,
          new Page.NavigateOptions()
              .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
              .setTimeout(properties.playwright().navigationTimeout().toMillis()));
      waitNetworkIdleBestEffort(page);
      driveInfyScroll(page);
      cleanupInfyUi(page);
      URI finalUri = safeUri(page.url(), url);
      return new RenderedPage(finalUri, page.content());
    } finally {
      page.close();
    }
  }

  private synchronized void ensureStarted() {
    if (running) {
      return;
    }
    playwright = playwrightFactory.get();
    browser =
        playwright
            .chromium()
            .launch(
                new BrowserType.LaunchOptions()
                    .setChannel("chromium")
                    .setHeadless(properties.playwright().headless()));
    running = true;
  }

  private synchronized BrowserContext ensureInfyContext() {
    if (infyContext != null) {
      return infyContext;
    }
    Path extensionDir = properties.playwright().infyExtensionDir();
    if (extensionDir == null || !Files.isDirectory(extensionDir)) {
      throw new IllegalStateException("Infy Scroll extension directory is not configured");
    }
    Path userDataDir = properties.playwright().infyUserDataDir();
    if (userDataDir == null) {
      throw new IllegalStateException("Infy Scroll user data directory is not configured");
    }
    String absoluteExtensionDir = extensionDir.toAbsolutePath().toString();
    infyContext =
        playwright
            .chromium()
            .launchPersistentContext(
                userDataDir,
                new BrowserType.LaunchPersistentContextOptions()
                    .setChannel("chromium")
                    .setHeadless(properties.playwright().headless())
                    .setViewportSize(
                        properties.playwright().viewportWidth(),
                        properties.playwright().viewportHeight())
                    .setArgs(
                        List.of(
                            "--disable-extensions-except=" + absoluteExtensionDir,
                            "--load-extension=" + absoluteExtensionDir)));
    for (Page page : new ArrayList<>(infyContext.pages())) {
      if (page.url() != null && page.url().startsWith("chrome-extension://")) {
        page.close();
      }
    }
    return infyContext;
  }

  private void driveInfyScroll(Page page) {
    long lastHeight = scrollHeight(page);
    int lastDividers = infyDividerCount(page);
    int stableRounds = 0;
    for (int i = 0;
        i < properties.playwright().infyMaxScrolls()
            && stableRounds < properties.playwright().infyStableRounds();
        i++) {
      page.evaluate(
          """
          () => window.scrollTo(
            0,
            Math.max(
              document.documentElement.scrollHeight || 0,
              document.body.scrollHeight || 0
            )
          )
          """);
      page.waitForTimeout(properties.playwright().infyScrollWait().toMillis());
      waitNetworkIdleBestEffort(page);
      long newHeight = scrollHeight(page);
      int newDividers = infyDividerCount(page);
      if (newHeight > lastHeight + 20 || newDividers > lastDividers) {
        stableRounds = 0;
        lastHeight = newHeight;
        lastDividers = newDividers;
      } else {
        stableRounds++;
      }
    }
  }

  private long scrollHeight(Page page) {
    Object value =
        page.evaluate(
            """
            () => Math.max(
              document.documentElement.scrollHeight || 0,
              document.body.scrollHeight || 0
            )
            """);
    return value instanceof Number number ? number.longValue() : 0;
  }

  private int infyDividerCount(Page page) {
    Object value =
        page.evaluate("() => document.querySelectorAll('[id^=\"infy-scroll-divider-\"]').length");
    return value instanceof Number number ? number.intValue() : 0;
  }

  private void cleanupInfyUi(Page page) {
    page.evaluate(
        """
        () => {
          document
            .querySelectorAll(
              '#infy-scroll-loading, #infy-scroll-overlay, [id^="infy-scroll-divider-"]'
            )
            .forEach(el => el.remove());
        }
        """);
  }

  private void waitNetworkIdleBestEffort(Page page) {
    try {
      page.waitForLoadState(
          LoadState.NETWORKIDLE,
          new Page.WaitForLoadStateOptions()
              .setTimeout(properties.playwright().networkIdleTimeout().toMillis()));
    } catch (RuntimeException e) {
      // Long polling pages can prevent NETWORKIDLE; rendered DOM is still usable.
    }
  }

  private URI safeUri(String pageUrl, String fallback) {
    if (pageUrl == null || pageUrl.isBlank()) {
      try {
        return URI.create(fallback);
      } catch (IllegalArgumentException e) {
        return null;
      }
    }
    try {
      return new URI(pageUrl);
    } catch (URISyntaxException e) {
      try {
        return URI.create(fallback);
      } catch (IllegalArgumentException ex) {
        return null;
      }
    }
  }

  @Override
  public synchronized void start() {
    if (properties.playwright().enabled()) {
      ensureStarted();
    }
  }

  @Override
  public synchronized void stop() {
    if (infyContext != null) {
      infyContext.close();
      infyContext = null;
    }
    if (browser != null) {
      browser.close();
      browser = null;
    }
    if (playwright != null) {
      playwright.close();
      playwright = null;
    }
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public boolean isAutoStartup() {
    return false;
  }
}
