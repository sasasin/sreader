package net.sasasin.sreader.service.extraction;

import java.net.URI;
import java.util.Objects;

/** One page's request/final URL and size for probe diagnostics (not fully persisted to DB). */
public record PaginationPageTrace(int pageNumber, URI requestedUri, URI finalUri, long byteSize) {

  public PaginationPageTrace {
    if (pageNumber < 1) {
      throw new IllegalArgumentException("pageNumber must be >= 1");
    }
    Objects.requireNonNull(requestedUri, "requestedUri must not be null");
    Objects.requireNonNull(finalUri, "finalUri must not be null");
    if (byteSize < 0) {
      throw new IllegalArgumentException("byteSize must not be negative");
    }
  }
}
