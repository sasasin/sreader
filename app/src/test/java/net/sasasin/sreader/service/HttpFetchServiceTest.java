package net.sasasin.sreader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import net.sasasin.sreader.config.FeedReaderProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HttpFetchServiceTest {

  @AfterEach
  void clearInterruptFlag() {
    Thread.interrupted();
  }

  @Test
  void getBuildsExpectedRequestAndReturnsUtf8Resource() throws Exception {
    HttpClient client = mock(HttpClient.class);
    URI requested = URI.create("https://example.test/start");
    URI finalUri = URI.create("https://example.test/final");
    stubByteResponse(
        client, response(200, finalUri, "本文".getBytes(StandardCharsets.UTF_8), Optional.empty()));

    HttpFetchService service = new HttpFetchService(properties(1, Duration.ofSeconds(2)), client);

    assertThat(service.get(requested))
        .isEqualTo(new HttpFetchService.FetchedResource(finalUri, "本文"));
    ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
    verify(client).send(request.capture(), any());
    assertThat(request.getValue().method()).isEqualTo("GET");
    assertThat(request.getValue().uri()).isEqualTo(requested);
    assertThat(request.getValue().headers().firstValue("User-Agent")).contains("test-agent");
    assertThat(request.getValue().headers().firstValue("Accept")).contains("*/*");
    assertThat(request.getValue().timeout()).contains(Duration.ofSeconds(2));
  }

  @Test
  void getRejectsStatusOutside2xxRange() throws Exception {
    HttpClient client = mock(HttpClient.class);
    stubByteResponse(
        client, response(199, URI.create("https://example.test/"), new byte[0], Optional.empty()));
    HttpFetchService service = new HttpFetchService(properties(0, Duration.ofSeconds(1)), client);

    assertThatIOException()
        .isThrownBy(() -> service.get(URI.create("https://example.test/a")))
        .withMessageContaining("GET")
        .withMessageContaining("https://example.test/a")
        .withMessageContaining("199");
    stubByteResponse(
        client, response(300, URI.create("https://example.test/"), new byte[0], Optional.empty()));
    assertThatIOException().isThrownBy(() -> service.get(URI.create("https://example.test/b")));
  }

  @Test
  void getDecodesDeclaredAndInvalidCharsets() throws Exception {
    HttpClient client = mock(HttpClient.class);
    URI uri = URI.create("https://example.test/");
    stubByteResponse(
        client,
        response(
            200,
            uri,
            "café".getBytes(Charset.forName("ISO-8859-1")),
            Optional.of("text/html; charset=ISO-8859-1")));
    HttpFetchService service = new HttpFetchService(properties(0, Duration.ofSeconds(1)), client);
    assertThat(service.get(uri).body()).isEqualTo("café");

    stubByteResponse(
        client,
        response(
            200,
            uri,
            "あ".getBytes(Charset.forName("Shift_JIS")),
            Optional.of("TEXT/HTML; charset=\"Shift_JIS\" ")));
    assertThat(service.get(uri).body()).isEqualTo("あ");

    stubByteResponse(
        client,
        response(
            200,
            uri,
            "utf8".getBytes(StandardCharsets.UTF_8),
            Optional.of("text/html; charset=missing")));
    assertThat(service.get(uri).body()).isEqualTo("utf8");

    stubByteResponse(
        client,
        response(200, uri, "fallback".getBytes(StandardCharsets.UTF_8), Optional.of("text/html")));
    assertThat(service.get(uri).body()).isEqualTo("fallback");
  }

  @Test
  void getRetriesIoExceptionsAndThrowsTheLastOne() throws Exception {
    HttpClient client = mock(HttpClient.class);
    IOException first = new IOException("first");
    IOException last = new IOException("last");
    doAnswer(
            invocation -> {
              throw first;
            })
        .doAnswer(
            invocation ->
                response(
                    200,
                    URI.create("https://example.test/"),
                    "ok".getBytes(StandardCharsets.UTF_8),
                    Optional.empty()))
        .when(client)
        .send(any(), any());
    HttpFetchService service = new HttpFetchService(properties(1, Duration.ofSeconds(1)), client);
    assertThat(service.get(URI.create("https://example.test/")).body()).isEqualTo("ok");
    verify(client, times(2)).send(any(), any());

    HttpClient failingClient = mock(HttpClient.class);
    doAnswer(
            invocation -> {
              throw first;
            })
        .doAnswer(
            invocation -> {
              throw last;
            })
        .when(failingClient)
        .send(any(), any());
    HttpFetchService failing =
        new HttpFetchService(properties(1, Duration.ofSeconds(1)), failingClient);
    assertThatThrownBy(() -> failing.get(URI.create("https://example.test/"))).isSameAs(last);
  }

  @Test
  void getAttemptsOnceForZeroRetriesAndDoesNotRetryInterruptions() throws Exception {
    HttpClient zero = mock(HttpClient.class);
    stubByteResponse(
        zero, response(200, URI.create("https://example.test/"), new byte[0], Optional.empty()));
    new HttpFetchService(properties(0, Duration.ofSeconds(1)), zero)
        .get(URI.create("https://example.test/"));
    verify(zero).send(any(), any());

    HttpClient interrupted = mock(HttpClient.class);
    doAnswer(
            invocation -> {
              throw new InterruptedException("stop");
            })
        .when(interrupted)
        .send(any(), any());
    assertThatThrownBy(
            () ->
                new HttpFetchService(properties(4, Duration.ofSeconds(1)), interrupted)
                    .get(URI.create("https://example.test/")))
        .isInstanceOf(InterruptedException.class);
    verify(interrupted).send(any(), any());
  }

  @Test
  void resolveRedirectResolvedWithoutRedirectKeepsRequestedUri() throws Exception {
    URI uri = URI.create("https://example.test/start");
    HttpClient client = mock(HttpClient.class);
    stubVoidResponse(client, voidResponse(200, uri));
    HttpFetchService service = new HttpFetchService(properties(0, Duration.ofMillis(200)), client);

    assertThat(service.resolveRedirect(uri)).isEqualTo(new RedirectResolution.Resolved(uri, uri));
  }

  @Test
  void resolveRedirectResolvedFollowsFinalUri() throws Exception {
    URI uri = URI.create("https://example.test/start");
    URI finalUri = URI.create("https://example.test/final");
    HttpClient client = mock(HttpClient.class);
    stubVoidResponse(client, voidResponse(399, finalUri));
    HttpFetchService service = new HttpFetchService(properties(0, Duration.ofMillis(200)), client);

    RedirectResolution resolution = service.resolveRedirect(uri);
    assertThat(resolution).isInstanceOf(RedirectResolution.Resolved.class);
    assertThat(resolution.effectiveUri()).isEqualTo(finalUri);
    ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
    verify(client).send(request.capture(), any());
    assertThat(request.getValue().method()).isEqualTo("HEAD");
    assertThat(request.getValue().timeout()).contains(Duration.ofSeconds(1));
  }

  @Test
  void resolveRedirectNon2xxIsFallbackWithHttpStatus() throws Exception {
    URI uri = URI.create("https://example.test/start");
    HttpClient client = mock(HttpClient.class);
    stubVoidResponse(client, voidResponse(400, URI.create("https://example.test/final")));
    HttpFetchService service = new HttpFetchService(properties(0, Duration.ofMillis(200)), client);

    RedirectResolution resolution = service.resolveRedirect(uri);
    assertThat(resolution).isInstanceOf(RedirectResolution.Fallback.class);
    RedirectResolution.Fallback fallback = (RedirectResolution.Fallback) resolution;
    assertThat(fallback.effectiveUri()).isEqualTo(uri);
    assertThat(fallback.failure().kind()).isEqualTo(FailureKind.HTTP_STATUS);
    assertThat(fallback.failure().stage()).isEqualTo(FailureStage.RESOLVE_REDIRECT);
  }

  @Test
  void resolveRedirectIoFailureIsFallback() throws Exception {
    URI uri = URI.create("https://example.test/start");
    HttpClient client = mock(HttpClient.class);
    doAnswer(
            invocation -> {
              throw new IOException("down");
            })
        .when(client)
        .send(any(), any());
    HttpFetchService service = new HttpFetchService(properties(0, Duration.ofMillis(200)), client);

    RedirectResolution.Fallback fallback =
        (RedirectResolution.Fallback) service.resolveRedirect(uri);
    assertThat(fallback.effectiveUri()).isEqualTo(uri);
    assertThat(fallback.failure().kind()).isEqualTo(FailureKind.IO);
    assertThat(fallback.failure().cause()).isPresent();
  }

  @Test
  void resolveRedirectInvalidInputIsFallback() throws Exception {
    URI uri = URI.create("https://example.test/start");
    HttpClient client = mock(HttpClient.class);
    doAnswer(
            invocation -> {
              throw new IllegalArgumentException("bad");
            })
        .when(client)
        .send(any(), any());
    HttpFetchService service = new HttpFetchService(properties(0, Duration.ofMillis(200)), client);

    RedirectResolution.Fallback fallback =
        (RedirectResolution.Fallback) service.resolveRedirect(uri);
    assertThat(fallback.failure().kind()).isEqualTo(FailureKind.INVALID_INPUT);
    assertThat(fallback.effectiveUri()).isEqualTo(uri);
  }

  @Test
  void resolveRedirectInterruptedIsFallbackAndSetsInterruptFlag() throws Exception {
    URI uri = URI.create("https://example.test/start");
    HttpClient client = mock(HttpClient.class);
    doAnswer(
            invocation -> {
              throw new InterruptedException("stop");
            })
        .when(client)
        .send(any(), any());
    HttpFetchService service = new HttpFetchService(properties(0, Duration.ofMillis(200)), client);

    RedirectResolution.Fallback fallback =
        (RedirectResolution.Fallback) service.resolveRedirect(uri);
    assertThat(fallback.failure().kind()).isEqualTo(FailureKind.INTERRUPTED);
    assertThat(fallback.failure().interrupted()).isTrue();
    assertThat(fallback.effectiveUri()).isEqualTo(uri);
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }

  private FeedReaderProperties properties(int retries, Duration timeout) {
    return new FeedReaderProperties(
        null,
        null,
        new FeedReaderProperties.Http("test-agent", Duration.ofSeconds(1), timeout, retries),
        null,
        null,
        null);
  }

  @SuppressWarnings("unchecked")
  private void stubByteResponse(HttpClient client, HttpResponse<byte[]> response) throws Exception {
    doAnswer(invocation -> response).when(client).send(any(), any());
  }

  @SuppressWarnings("unchecked")
  private void stubVoidResponse(HttpClient client, HttpResponse<Void> response) throws Exception {
    doAnswer(invocation -> response).when(client).send(any(), any());
  }

  @SuppressWarnings("unchecked")
  private HttpResponse<byte[]> response(
      int status, URI uri, byte[] body, Optional<String> contentType) {
    HttpResponse<byte[]> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.uri()).thenReturn(uri);
    when(response.body()).thenReturn(body);
    HttpHeaders headers = mock(HttpHeaders.class);
    when(headers.firstValue("content-type")).thenReturn(contentType);
    when(response.headers()).thenReturn(headers);
    return response;
  }

  @SuppressWarnings("unchecked")
  private HttpResponse<Void> voidResponse(int status, URI uri) {
    HttpResponse<Void> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.uri()).thenReturn(uri);
    return response;
  }
}
