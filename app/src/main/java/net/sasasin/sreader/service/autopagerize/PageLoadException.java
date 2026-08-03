package net.sasasin.sreader.service.autopagerize;

import java.util.Objects;
import net.sasasin.sreader.service.outcome.FailureKind;

/**
 * Failure while loading a page through an {@link ArticlePageSession}. Carries a structured {@link
 * FailureKind} so callers can map HTTP status, I/O, interrupt, and invalid input without parsing
 * message strings.
 */
public class PageLoadException extends Exception {

  private final FailureKind kind;

  public PageLoadException(String message) {
    this(FailureKind.IO, message);
  }

  public PageLoadException(String message, Throwable cause) {
    this(FailureKind.IO, message, cause);
  }

  public PageLoadException(FailureKind kind, String message) {
    super(message);
    this.kind = Objects.requireNonNull(kind, "kind must not be null");
  }

  public PageLoadException(FailureKind kind, String message, Throwable cause) {
    super(message, cause);
    this.kind = Objects.requireNonNull(kind, "kind must not be null");
  }

  public FailureKind kind() {
    return kind;
  }
}
