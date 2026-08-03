package net.sasasin.sreader.service.http;

import java.util.Objects;
import net.sasasin.sreader.service.autopagerize.ArticlePageSession;
import org.springframework.stereotype.Component;

/**
 * Opens cookie-isolated {@link ArticlePageSession} instances for HTTP AutoPagerize extraction. Each
 * call yields a fresh session that must not share cookie state with other article chains.
 */
@Component
public class HttpArticlePageSessionFactory {

  private final HttpTransport transport;

  public HttpArticlePageSessionFactory(HttpTransport transport) {
    this.transport = Objects.requireNonNull(transport, "transport must not be null");
  }

  public ArticlePageSession open() {
    return new HttpArticlePageSession(transport.newCookieIsolatedClient(), transport);
  }
}
