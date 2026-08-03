package net.sasasin.sreader.service.http;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sasasin.sreader.config.FeedReaderProperties;
import org.springframework.stereotype.Component;

/**
 * Shared HTTP request construction, retry, and response decoding used by feed/article fetch and
 * AutoPagerize page sessions. Does not own a long-lived {@link HttpClient}; callers supply one so
 * cookie isolation can be scoped per article chain.
 */
@Component
public class HttpTransport {

  private static final Pattern CHARSET_PATTERN =
      Pattern.compile("charset=([^;]+)", Pattern.CASE_INSENSITIVE);

  private final FeedReaderProperties properties;

  public HttpTransport(FeedReaderProperties properties) {
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
  }

  /**
   * Builds a cookie-isolated client that follows redirects with {@link HttpClient.Redirect#NORMAL}.
   */
  public HttpClient newCookieIsolatedClient() {
    CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    return HttpClient.newBuilder()
        .connectTimeout(properties.http().connectTimeout())
        .followRedirects(HttpClient.Redirect.NORMAL)
        .cookieHandler(cookies)
        .build();
  }

  public HttpRequest.Builder baseRequest(URI uri) {
    return HttpRequest.newBuilder(uri)
        .header("User-Agent", properties.http().userAgent())
        .header("Accept", "*/*");
  }

  public HttpRequest getRequest(URI uri) {
    return baseRequest(uri).GET().timeout(properties.http().readTimeout()).build();
  }

  public HttpRequest headRequest(URI uri) {
    return baseRequest(uri)
        .method("HEAD", HttpRequest.BodyPublishers.noBody())
        .timeout(Duration.ofSeconds(Math.max(1, properties.http().readTimeout().toSeconds())))
        .build();
  }

  /**
   * Sends a request with the configured retry count. Retries only {@link IOException};
   * interruptions are not retried.
   */
  public HttpResponse<byte[]> sendWithRetry(HttpClient client, HttpRequest request)
      throws IOException, InterruptedException {
    Objects.requireNonNull(client, "client must not be null");
    Objects.requireNonNull(request, "request must not be null");
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

  public HttpResponse<Void> sendDiscarding(HttpClient client, HttpRequest request)
      throws IOException, InterruptedException {
    Objects.requireNonNull(client, "client must not be null");
    Objects.requireNonNull(request, "request must not be null");
    return client.send(request, HttpResponse.BodyHandlers.discarding());
  }

  /**
   * Performs GET with retry and requires a 2xx status. Returns final URI, decoded body, and raw
   * response body length.
   */
  public FetchedBytes get(HttpClient client, URI uri) throws IOException, InterruptedException {
    HttpResponse<byte[]> response = sendWithRetry(client, getRequest(uri));
    ensure2xx(uri, response.statusCode());
    byte[] body = response.body() == null ? new byte[0] : response.body();
    return new FetchedBytes(response.uri(), decode(response), body.length);
  }

  public void ensure2xx(URI requestedUri, int statusCode) throws IOException {
    if (statusCode < 200 || statusCode >= 300) {
      throw new IOException("GET " + requestedUri + " returned HTTP " + statusCode);
    }
  }

  public String decode(HttpResponse<byte[]> response) {
    Charset charset =
        response
            .headers()
            .firstValue("content-type")
            .flatMap(this::charsetFromContentType)
            .orElse(StandardCharsets.UTF_8);
    byte[] body = response.body() == null ? new byte[0] : response.body();
    return new String(body, charset);
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

  public FeedReaderProperties.Http httpProperties() {
    return properties.http();
  }

  /** Decoded GET response with raw byte length of the response body. */
  public record FetchedBytes(URI finalUri, String body, long rawByteLength) {
    public FetchedBytes {
      Objects.requireNonNull(finalUri, "finalUri must not be null");
      Objects.requireNonNull(body, "body must not be null");
      if (rawByteLength < 0) {
        throw new IllegalArgumentException("rawByteLength must not be negative");
      }
    }
  }
}
