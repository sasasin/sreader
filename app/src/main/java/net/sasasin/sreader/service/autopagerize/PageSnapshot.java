package net.sasasin.sreader.service.autopagerize;

import java.net.URI;
import java.util.Objects;

/**
 * One loaded HTML document. {@code byteSize} is the adapter-reported raw size, or a documented
 * UTF-8 approximation when raw bytes are unavailable. Must not be negative.
 */
public record PageSnapshot(URI requestedUri, URI finalUri, String html, long byteSize) {

  public PageSnapshot {
    Objects.requireNonNull(requestedUri, "requestedUri must not be null");
    Objects.requireNonNull(finalUri, "finalUri must not be null");
    Objects.requireNonNull(html, "html must not be null");
    if (byteSize < 0) {
      throw new IllegalArgumentException("byteSize must not be negative");
    }
  }

  /** Approximates byte size from UTF-8 encoding of {@code html}. */
  public static long utf8ByteSize(String html) {
    Objects.requireNonNull(html, "html must not be null");
    return html.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
  }

  public static PageSnapshot ofUtf8(URI requestedUri, URI finalUri, String html) {
    return new PageSnapshot(requestedUri, finalUri, html, utf8ByteSize(html));
  }
}
