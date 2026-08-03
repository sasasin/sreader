package net.sasasin.sreader.service.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Objects;
import net.sasasin.sreader.service.autopagerize.ArticlePageSession;
import net.sasasin.sreader.service.autopagerize.PageLoadException;
import net.sasasin.sreader.service.autopagerize.PageSnapshot;
import net.sasasin.sreader.service.outcome.FailureKind;

/**
 * Cookie-isolated HTTP session for one article pagination chain. Shares User-Agent, timeouts,
 * redirect policy, retry, and charset decoding with {@link HttpFetchService} via {@link
 * HttpTransport}.
 */
final class HttpArticlePageSession implements ArticlePageSession {

  private final HttpClient client;
  private final HttpTransport transport;
  private boolean closed;

  HttpArticlePageSession(HttpClient client, HttpTransport transport) {
    this.client = Objects.requireNonNull(client, "client must not be null");
    this.transport = Objects.requireNonNull(transport, "transport must not be null");
  }

  @Override
  public PageSnapshot load(URI uri) throws PageLoadException {
    Objects.requireNonNull(uri, "uri must not be null");
    if (closed) {
      throw new PageLoadException(FailureKind.INVALID_INPUT, "HTTP article page session is closed");
    }
    try {
      HttpTransport.FetchedBytes fetched = transport.get(client, uri);
      return new PageSnapshot(uri, fetched.finalUri(), fetched.body(), fetched.rawByteLength());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new PageLoadException(
          FailureKind.INTERRUPTED, "Article page load interrupted for " + uri, e);
    } catch (IOException e) {
      FailureKind kind = failureKindFor(e);
      throw new PageLoadException(
          kind, "Article page load failed for " + uri + ": " + message(e), e);
    } catch (IllegalArgumentException e) {
      throw new PageLoadException(
          FailureKind.INVALID_INPUT,
          "Article page load invalid URI for " + uri + ": " + message(e),
          e);
    }
  }

  @Override
  public void close() {
    closed = true;
  }

  private static FailureKind failureKindFor(IOException e) {
    String message = e.getMessage();
    if (message != null && message.contains(" returned HTTP ")) {
      return FailureKind.HTTP_STATUS;
    }
    // java.net.http maps connect/read timeouts to IOException subtypes / messages.
    if (e instanceof java.net.http.HttpTimeoutException
        || (message != null && message.toLowerCase().contains("timed out"))) {
      return FailureKind.IO;
    }
    return FailureKind.IO;
  }

  private static String message(Throwable e) {
    String message = e.getMessage();
    return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
  }
}
