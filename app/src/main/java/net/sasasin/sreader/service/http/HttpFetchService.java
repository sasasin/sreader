package net.sasasin.sreader.service.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Objects;
import net.sasasin.sreader.service.outcome.FailureKind;
import net.sasasin.sreader.service.outcome.FailureStage;
import net.sasasin.sreader.service.outcome.OperationFailure;
import org.springframework.stereotype.Service;

/**
 * Shared HTTP GET and redirect resolution for feed/article fetch paths that do not require
 * per-chain cookie isolation. Uses the application-scoped {@link HttpClient} bean.
 */
@Service
public class HttpFetchService {

  private final HttpTransport transport;
  private final HttpClient client;

  public HttpFetchService(HttpTransport transport, HttpClient client) {
    this.transport = Objects.requireNonNull(transport, "transport must not be null");
    this.client = Objects.requireNonNull(client, "client must not be null");
  }

  public FetchedResource get(URI uri) throws IOException, InterruptedException {
    HttpTransport.FetchedBytes fetched = transport.get(client, uri);
    return new FetchedResource(fetched.finalUri(), fetched.body());
  }

  public RedirectResolution resolveRedirect(URI uri) {
    try {
      HttpResponse<Void> response = transport.sendDiscarding(client, transport.headRequest(uri));
      if (response.statusCode() >= 200 && response.statusCode() < 400) {
        return new RedirectResolution.Resolved(uri, response.uri());
      }
      return new RedirectResolution.Fallback(
          uri,
          OperationFailure.of(
              FailureStage.RESOLVE_REDIRECT,
              FailureKind.HTTP_STATUS,
              uri.toString(),
              "HEAD " + uri + " returned HTTP " + response.statusCode()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new RedirectResolution.Fallback(
          uri,
          OperationFailure.of(
              FailureStage.RESOLVE_REDIRECT,
              FailureKind.INTERRUPTED,
              uri.toString(),
              "Redirect resolution interrupted for " + uri,
              e));
    } catch (IOException e) {
      return new RedirectResolution.Fallback(
          uri,
          OperationFailure.of(
              FailureStage.RESOLVE_REDIRECT,
              FailureKind.IO,
              uri.toString(),
              "Redirect resolution I/O failure for " + uri + ": " + e.getMessage(),
              e));
    } catch (IllegalArgumentException e) {
      return new RedirectResolution.Fallback(
          uri,
          OperationFailure.of(
              FailureStage.RESOLVE_REDIRECT,
              FailureKind.INVALID_INPUT,
              uri.toString(),
              "Redirect resolution invalid input for " + uri + ": " + e.getMessage(),
              e));
    }
  }

  public record FetchedResource(URI uri, String body) {}
}
