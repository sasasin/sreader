package net.sasasin.sreader.service.autopagerize;

/** Failure while loading a page through an {@link ArticlePageSession}. */
public class PageLoadException extends Exception {

  public PageLoadException(String message) {
    super(message);
  }

  public PageLoadException(String message, Throwable cause) {
    super(message, cause);
  }
}
