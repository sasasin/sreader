package net.sasasin.sreader.service.autopagerize;

import java.net.URI;

/**
 * Transport-agnostic page loader for AutoPagerize pagination. HTTP and Playwright adapters
 * implement this; unit tests use fakes.
 */
public interface ArticlePageSession extends AutoCloseable {

  PageSnapshot load(URI uri) throws PageLoadException;

  @Override
  default void close() {
    // optional resource cleanup for network adapters
  }
}
