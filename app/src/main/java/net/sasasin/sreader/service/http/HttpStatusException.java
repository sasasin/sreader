package net.sasasin.sreader.service.http;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

/** HTTP response status outside the successful 2xx range. */
public final class HttpStatusException extends IOException {

  private final URI requestedUri;
  private final int statusCode;

  public HttpStatusException(URI requestedUri, int statusCode) {
    super("GET " + requestedUri + " returned HTTP " + statusCode);
    this.requestedUri = Objects.requireNonNull(requestedUri, "requestedUri must not be null");
    this.statusCode = statusCode;
  }

  public URI requestedUri() {
    return requestedUri;
  }

  public int statusCode() {
    return statusCode;
  }
}
