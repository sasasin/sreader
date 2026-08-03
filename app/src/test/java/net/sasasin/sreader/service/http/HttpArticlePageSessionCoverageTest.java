package net.sasasin.sreader.service.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import net.sasasin.sreader.service.autopagerize.PageLoadException;
import net.sasasin.sreader.service.autopagerize.PageSnapshot;
import net.sasasin.sreader.service.outcome.FailureKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpArticlePageSessionCoverageTest {

  @AfterEach
  void clearInterrupt() {
    Thread.interrupted();
  }

  @Test
  void closedSessionRejectsLoad() throws Exception {
    HttpTransport transport = mock(HttpTransport.class);
    HttpArticlePageSession session = new HttpArticlePageSession(mock(HttpClient.class), transport);
    session.close();
    assertThatThrownBy(() -> session.load(URI.create("https://example.test/")))
        .isInstanceOf(PageLoadException.class)
        .satisfies(
            ex -> assertThat(((PageLoadException) ex).kind()).isEqualTo(FailureKind.INVALID_INPUT));
  }

  @Test
  void mapsHttpStatusException() throws Exception {
    HttpTransport transport = mock(HttpTransport.class);
    URI uri = URI.create("https://example.test/a");
    when(transport.get(any(), eq(uri))).thenThrow(new HttpStatusException(uri, 404));
    HttpArticlePageSession session = new HttpArticlePageSession(mock(HttpClient.class), transport);
    assertThatThrownBy(() -> session.load(uri))
        .isInstanceOf(PageLoadException.class)
        .satisfies(
            ex -> assertThat(((PageLoadException) ex).kind()).isEqualTo(FailureKind.HTTP_STATUS));
  }

  @Test
  void mapsTimeoutExceptionAndTimedOutMessage() throws Exception {
    HttpTransport transport = mock(HttpTransport.class);
    URI uri = URI.create("https://example.test/a");
    when(transport.get(any(), eq(uri))).thenThrow(new HttpTimeoutException("request timed out"));
    HttpArticlePageSession session = new HttpArticlePageSession(mock(HttpClient.class), transport);
    assertThatThrownBy(() -> session.load(uri))
        .isInstanceOf(PageLoadException.class)
        .satisfies(ex -> assertThat(((PageLoadException) ex).kind()).isEqualTo(FailureKind.IO));

    when(transport.get(any(), eq(uri))).thenThrow(new IOException("connection timed out"));
    assertThatThrownBy(() -> session.load(uri))
        .isInstanceOf(PageLoadException.class)
        .satisfies(ex -> assertThat(((PageLoadException) ex).kind()).isEqualTo(FailureKind.IO));
  }

  @Test
  void mapsGenericIoAndBlankMessage() throws Exception {
    HttpTransport transport = mock(HttpTransport.class);
    URI uri = URI.create("https://example.test/a");
    when(transport.get(any(), eq(uri))).thenThrow(new IOException("   "));
    HttpArticlePageSession session = new HttpArticlePageSession(mock(HttpClient.class), transport);
    assertThatThrownBy(() -> session.load(uri))
        .isInstanceOf(PageLoadException.class)
        .satisfies(
            ex -> {
              assertThat(((PageLoadException) ex).kind()).isEqualTo(FailureKind.IO);
              assertThat(ex.getMessage()).contains("IOException");
            });
  }

  @Test
  void mapsIoExceptionWithNullMessage() throws Exception {
    HttpTransport transport = mock(HttpTransport.class);
    URI uri = URI.create("https://example.test/a");
    when(transport.get(any(), eq(uri))).thenThrow(new IOException((String) null));
    HttpArticlePageSession session = new HttpArticlePageSession(mock(HttpClient.class), transport);
    assertThatThrownBy(() -> session.load(uri))
        .isInstanceOf(PageLoadException.class)
        .satisfies(
            ex -> {
              assertThat(((PageLoadException) ex).kind()).isEqualTo(FailureKind.IO);
              assertThat(ex.getMessage()).contains("IOException");
            });
  }

  @Test
  void mapsInterruptedException() throws Exception {
    HttpTransport transport = mock(HttpTransport.class);
    URI uri = URI.create("https://example.test/a");
    when(transport.get(any(), eq(uri))).thenThrow(new InterruptedException("stop"));
    HttpArticlePageSession session = new HttpArticlePageSession(mock(HttpClient.class), transport);
    assertThatThrownBy(() -> session.load(uri))
        .isInstanceOf(PageLoadException.class)
        .satisfies(
            ex -> assertThat(((PageLoadException) ex).kind()).isEqualTo(FailureKind.INTERRUPTED));
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }

  @Test
  void mapsIllegalArgumentException() throws Exception {
    HttpTransport transport = mock(HttpTransport.class);
    URI uri = URI.create("https://example.test/a");
    when(transport.get(any(), eq(uri))).thenThrow(new IllegalArgumentException("bad uri"));
    HttpArticlePageSession session = new HttpArticlePageSession(mock(HttpClient.class), transport);
    assertThatThrownBy(() -> session.load(uri))
        .isInstanceOf(PageLoadException.class)
        .satisfies(
            ex -> assertThat(((PageLoadException) ex).kind()).isEqualTo(FailureKind.INVALID_INPUT));
  }

  @Test
  void successfulLoadReturnsSnapshot() throws Exception {
    HttpTransport transport = mock(HttpTransport.class);
    URI requested = URI.create("https://example.test/start");
    URI finalUri = URI.create("https://example.test/final");
    when(transport.get(any(), eq(requested)))
        .thenReturn(new HttpTransport.FetchedBytes(finalUri, "<html/>", 7));
    HttpArticlePageSession session = new HttpArticlePageSession(mock(HttpClient.class), transport);
    PageSnapshot page = session.load(requested);
    assertThat(page.requestedUri()).isEqualTo(requested);
    assertThat(page.finalUri()).isEqualTo(finalUri);
    assertThat(page.html()).isEqualTo("<html/>");
    assertThat(page.byteSize()).isEqualTo(7);
  }
}
