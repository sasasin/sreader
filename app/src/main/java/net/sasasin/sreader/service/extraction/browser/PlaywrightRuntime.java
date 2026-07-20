package net.sasasin.sreader.service.extraction.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;
import net.sasasin.sreader.config.FeedReaderProperties;

/**
 * Owns the Playwright process and the regular Chromium {@link Browser}.
 *
 * <p>Not a public concurrent API: all access is expected to be serialized by {@link
 * PlaywrightHtmlSource}.
 */
final class PlaywrightRuntime {

  private final FeedReaderProperties.Playwright settings;
  private final Supplier<Playwright> playwrightFactory;

  private Playwright playwright;
  private Browser browser;
  private volatile boolean running;

  PlaywrightRuntime(
      FeedReaderProperties.Playwright settings, Supplier<Playwright> playwrightFactory) {
    this.settings = Objects.requireNonNull(settings, "settings must not be null");
    this.playwrightFactory =
        Objects.requireNonNull(playwrightFactory, "playwrightFactory must not be null");
  }

  void start() {
    if (running) {
      return;
    }
    Playwright created = null;
    try {
      created = playwrightFactory.get();
      Browser launched =
          created
              .chromium()
              .launch(
                  new BrowserType.LaunchOptions()
                      .setChannel("chromium")
                      .setHeadless(settings.headless()));
      this.playwright = created;
      this.browser = launched;
      this.running = true;
    } catch (RuntimeException startupFailure) {
      this.browser = null;
      this.playwright = null;
      this.running = false;
      if (created != null) {
        RuntimeException primary = PlaywrightCloseSupport.close(startupFailure, created::close);
        PlaywrightCloseSupport.throwIfPresent(primary);
      }
      throw startupFailure;
    }
  }

  Browser browser() {
    ensureStarted();
    return browser;
  }

  BrowserType chromium() {
    ensureStarted();
    return playwright.chromium();
  }

  BrowserContext launchPersistentContext(
      Path userDataDir, BrowserType.LaunchPersistentContextOptions options) {
    return chromium().launchPersistentContext(userDataDir, options);
  }

  boolean isRunning() {
    return running;
  }

  void stop() {
    if (!running && browser == null && playwright == null) {
      return;
    }
    RuntimeException primary = null;
    if (browser != null) {
      Browser toClose = browser;
      browser = null;
      primary = PlaywrightCloseSupport.close(primary, toClose::close);
    }
    if (playwright != null) {
      Playwright toClose = playwright;
      playwright = null;
      primary = PlaywrightCloseSupport.close(primary, toClose::close);
    }
    running = false;
    PlaywrightCloseSupport.throwIfPresent(primary);
  }

  private void ensureStarted() {
    if (!running) {
      start();
    }
  }
}
