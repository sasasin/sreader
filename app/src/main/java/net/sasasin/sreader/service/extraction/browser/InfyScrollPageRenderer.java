package net.sasasin.sreader.service.extraction.browser;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.sasasin.sreader.config.FeedReaderProperties;

/**
 * Owns the Infy Scroll persistent BrowserContext: lazy launch, reuse across renders, and close on
 * application stop. Scroll algorithm lives in {@link InfyScrollDriver}.
 */
final class InfyScrollPageRenderer implements AutoCloseable {

  private final FeedReaderProperties.Playwright settings;
  private final PlaywrightRuntime runtime;
  private final PlaywrightPageNavigator navigator;
  private final InfyScrollDriver driver;

  private BrowserContext infyContext;

  InfyScrollPageRenderer(
      FeedReaderProperties.Playwright settings,
      PlaywrightRuntime runtime,
      PlaywrightPageNavigator navigator,
      InfyScrollDriver driver) {
    this.settings = Objects.requireNonNull(settings, "settings must not be null");
    this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
    this.navigator = Objects.requireNonNull(navigator, "navigator must not be null");
    this.driver = Objects.requireNonNull(driver, "driver must not be null");
  }

  RenderedPage render(URI requestedUri) {
    Objects.requireNonNull(requestedUri, "requestedUri must not be null");
    BrowserContext context = ensureInfyContext();
    Page page = context.newPage();
    RuntimeException primary = null;
    RenderedPage result = null;
    try {
      navigator.navigate(page, requestedUri);
      navigator.waitNetworkIdleBestEffort(page);
      driver.drive(page);
      driver.cleanup(page);
      result = navigator.capture(page, requestedUri);
    } catch (RuntimeException e) {
      primary = e;
    } finally {
      primary = PlaywrightCloseSupport.close(primary, page::close);
    }
    if (primary != null) {
      throw primary;
    }
    return result;
  }

  @Override
  public void close() {
    if (infyContext == null) {
      return;
    }
    BrowserContext toClose = infyContext;
    infyContext = null;
    toClose.close();
  }

  private BrowserContext ensureInfyContext() {
    if (infyContext != null) {
      return infyContext;
    }
    Path extensionDir = settings.infyExtensionDir();
    if (extensionDir == null || !Files.isDirectory(extensionDir)) {
      throw new IllegalStateException("Infy Scroll extension directory is not configured");
    }
    Path userDataDir = settings.infyUserDataDir();
    if (userDataDir == null) {
      throw new IllegalStateException("Infy Scroll user data directory is not configured");
    }
    String absoluteExtensionDir = extensionDir.toAbsolutePath().toString();
    BrowserContext launched =
        runtime.launchPersistentContext(
            userDataDir,
            new BrowserType.LaunchPersistentContextOptions()
                .setChannel("chromium")
                .setHeadless(settings.headless())
                .setViewportSize(settings.viewportWidth(), settings.viewportHeight())
                .setArgs(
                    List.of(
                        "--disable-extensions-except=" + absoluteExtensionDir,
                        "--load-extension=" + absoluteExtensionDir)));
    try {
      // Snapshot copy so concurrent page close does not mutate the live list under iteration.
      for (Page page : new ArrayList<>(launched.pages())) {
        String url = page.url();
        if (url != null && url.startsWith("chrome-extension://")) {
          page.close();
        }
      }
    } catch (RuntimeException cleanupFailure) {
      // Do not cache a half-cleaned context; next Infy render may retry launch.
      RuntimeException primary = PlaywrightCloseSupport.close(cleanupFailure, launched::close);
      PlaywrightCloseSupport.throwIfPresent(primary);
      throw cleanupFailure;
    }
    infyContext = launched;
    return infyContext;
  }
}
