package net.sasasin.sreader.service;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import net.sasasin.sreader.domain.FullTextMethod;

/** Metadata for a probe operation, without body text. */
public record ProbeDocument(
    URI inputUrl, URI finalUrl, Optional<String> title, FullTextMethod method) {

  public ProbeDocument {
    Objects.requireNonNull(inputUrl, "inputUrl must not be null");
    Objects.requireNonNull(finalUrl, "finalUrl must not be null");
    title = Objects.requireNonNull(title, "title must not be null");
    Objects.requireNonNull(method, "method must not be null");
  }
}
