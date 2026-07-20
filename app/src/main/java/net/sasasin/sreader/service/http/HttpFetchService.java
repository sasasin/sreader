package net.sasasin.sreader.service.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sasasin.sreader.config.FeedReaderProperties;
import net.sasasin.sreader.service.outcome.FailureKind;
import net.sasasin.sreader.service.outcome.FailureStage;
import net.sasasin.sreader.service.outcome.OperationFailure;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class HttpFetchService {

  private static final Pattern CHARSET_PATTERN =
      Pattern.compile("charset=([^;]+)", Pattern.CASE_INSENSITIVE);

  private final FeedReaderProperties properties;
  private final HttpClient client;

  @Autowired
  public HttpFetchService(FeedReaderProperties properties) {
    this(properties, createClient(properties));
  }

  HttpFetchService(FeedReaderProperties properties, HttpClient client) {
    this.properties = properties;
    this.client = client;
  }

  private static HttpClient createClient(FeedReaderProperties properties) {
    return HttpClient.newBuilder()
        .connectTimeout(properties.http().connectTimeout())
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
  }

  public FetchedResource get(URI uri) throws IOException, InterruptedException {
    HttpRequest request = baseRequest(uri).GET().timeout(properties.http().readTimeout()).build();
    HttpResponse<byte[]> response = sendWithRetry(request);
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IOException("GET " + uri + " returned HTTP " + response.statusCode());
    }
    return new FetchedResource(response.uri(), decode(response));
  }

  public RedirectResolution resolveRedirect(URI uri) {
    try {
      HttpRequest request =
          baseRequest(uri)
              .method("HEAD", HttpRequest.BodyPublishers.noBody())
              .timeout(Duration.ofSeconds(Math.max(1, properties.http().readTimeout().toSeconds())))
              .build();
      HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
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

  private HttpRequest.Builder baseRequest(URI uri) {
    return HttpRequest.newBuilder(uri)
        .header("User-Agent", properties.http().userAgent())
        .header("Accept", "*/*");
  }

  private HttpResponse<byte[]> sendWithRetry(HttpRequest request)
      throws IOException, InterruptedException {
    IOException last = null;
    int attempts = Math.max(1, properties.http().retryCount() + 1);
    for (int i = 0; i < attempts; i++) {
      try {
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
      } catch (IOException e) {
        last = e;
      }
    }
    throw last;
  }

  private String decode(HttpResponse<byte[]> response) {
    Charset charset =
        response
            .headers()
            .firstValue("content-type")
            .flatMap(this::charsetFromContentType)
            .orElse(StandardCharsets.UTF_8);
    return new String(response.body(), charset);
  }

  private Optional<Charset> charsetFromContentType(String contentType) {
    Matcher matcher = CHARSET_PATTERN.matcher(contentType);
    if (!matcher.find()) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          Charset.forName(matcher.group(1).trim().replace("\"", "").toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  public record FetchedResource(URI uri, String body) {}
}
