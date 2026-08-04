package net.sasasin.sreader.service.extraction.browser;

import java.util.Objects;
import net.sasasin.sreader.config.FeedReaderProperties;

/**
 * Coordinates start/stop order for Playwright resources.
 *
 * <p>Stop order: regular Browser, then Playwright process.
 */
final class PlaywrightResourceLifecycle {

  private final FeedReaderProperties.Playwright settings;
  private final PlaywrightRuntime runtime;

  PlaywrightResourceLifecycle(FeedReaderProperties.Playwright settings, PlaywrightRuntime runtime) {
    this.settings = Objects.requireNonNull(settings, "settings must not be null");
    this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
  }

  void start() {
    if (!settings.enabled()) {
      return;
    }
    runtime.start();
  }

  void stop() {
    RuntimeException primary = PlaywrightCloseSupport.close(null, runtime::stop);
    PlaywrightCloseSupport.throwIfPresent(primary);
  }

  boolean isRunning() {
    return runtime.isRunning();
  }
}
