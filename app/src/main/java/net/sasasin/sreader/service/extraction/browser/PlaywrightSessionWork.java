package net.sasasin.sreader.service.extraction.browser;

import net.sasasin.sreader.service.autopagerize.ArticlePageSession;

/**
 * Work executed against a short-lived standard Playwright {@link ArticlePageSession} while the
 * {@link PlaywrightHtmlSource} monitor is held for the whole chain.
 *
 * @param <T> result type
 */
@FunctionalInterface
public interface PlaywrightSessionWork<T> {

  T apply(ArticlePageSession session);
}
