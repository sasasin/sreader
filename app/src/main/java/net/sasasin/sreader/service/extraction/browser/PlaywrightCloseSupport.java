package net.sasasin.sreader.service.extraction.browser;

/**
 * Closes Playwright-owned resources without aborting subsequent cleanup on the first failure.
 * Primary failures are preserved; later close failures are attached as suppressed exceptions.
 */
final class PlaywrightCloseSupport {

  private PlaywrightCloseSupport() {}

  static RuntimeException close(RuntimeException primary, Runnable closeAction) {
    try {
      closeAction.run();
      return primary;
    } catch (RuntimeException closeFailure) {
      if (primary == null) {
        return closeFailure;
      }
      primary.addSuppressed(closeFailure);
      return primary;
    }
  }

  static void throwIfPresent(RuntimeException primary) {
    if (primary != null) {
      throw primary;
    }
  }
}
