package net.sasasin.sreader.service.extraction.browser;

import com.microsoft.playwright.Page;
import java.util.Objects;
import net.sasasin.sreader.config.FeedReaderProperties;

/**
 * Infy Scroll convergence loop and overlay cleanup. Does not own page or browser lifecycle.
 *
 * <p>Height growth below {@link #MIN_HEIGHT_GROWTH_PX} is treated as noise, not content growth.
 */
final class InfyScrollDriver {

  /** Minimum scroll-height increase (px) that counts as growth. */
  static final int MIN_HEIGHT_GROWTH_PX = 20;

  private static final String SCROLL_SCRIPT =
      """
      () => window.scrollTo(
        0,
        Math.max(
          document.documentElement.scrollHeight || 0,
          document.body.scrollHeight || 0
        )
      )
      """;

  private static final String HEIGHT_SCRIPT =
      """
      () => Math.max(
        document.documentElement.scrollHeight || 0,
        document.body.scrollHeight || 0
      )
      """;

  private static final String DIVIDER_COUNT_SCRIPT =
      "() => document.querySelectorAll('[id^=\"infy-scroll-divider-\"]').length";

  private static final String CLEANUP_SCRIPT =
      """
      () => {
        document
          .querySelectorAll(
            '#infy-scroll-loading, #infy-scroll-overlay, [id^="infy-scroll-divider-"]'
          )
          .forEach(el => el.remove());
      }
      """;

  private final FeedReaderProperties.Playwright settings;
  private final PlaywrightPageNavigator navigator;

  InfyScrollDriver(FeedReaderProperties.Playwright settings, PlaywrightPageNavigator navigator) {
    this.settings = Objects.requireNonNull(settings, "settings must not be null");
    this.navigator = Objects.requireNonNull(navigator, "navigator must not be null");
  }

  void drive(Page page) {
    Objects.requireNonNull(page, "page must not be null");
    long lastHeight = scrollHeight(page);
    int lastDividers = infyDividerCount(page);
    int stableRounds = 0;
    for (int i = 0;
        i < settings.infyMaxScrolls() && stableRounds < settings.infyStableRounds();
        i++) {
      page.evaluate(SCROLL_SCRIPT);
      page.waitForTimeout(settings.infyScrollWait().toMillis());
      navigator.waitNetworkIdleBestEffort(page);
      long newHeight = scrollHeight(page);
      int newDividers = infyDividerCount(page);
      if (newHeight > lastHeight + MIN_HEIGHT_GROWTH_PX || newDividers > lastDividers) {
        stableRounds = 0;
        lastHeight = newHeight;
        lastDividers = newDividers;
      } else {
        stableRounds++;
      }
    }
  }

  void cleanup(Page page) {
    Objects.requireNonNull(page, "page must not be null");
    page.evaluate(CLEANUP_SCRIPT);
  }

  private long scrollHeight(Page page) {
    Object value = page.evaluate(HEIGHT_SCRIPT);
    return value instanceof Number number ? number.longValue() : 0;
  }

  private int infyDividerCount(Page page) {
    Object value = page.evaluate(DIVIDER_COUNT_SCRIPT);
    return value instanceof Number number ? number.intValue() : 0;
  }
}
