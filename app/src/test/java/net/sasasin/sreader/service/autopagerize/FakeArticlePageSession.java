package net.sasasin.sreader.service.autopagerize;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** In-memory {@link ArticlePageSession} for unit tests. */
final class FakeArticlePageSession implements ArticlePageSession {

  private final Map<URI, Function<URI, PageSnapshot>> loaders = new LinkedHashMap<>();
  private final Map<URI, PageLoadException> failures = new LinkedHashMap<>();

  FakeArticlePageSession put(URI uri, String html) {
    return put(uri, uri, html);
  }

  FakeArticlePageSession put(URI requested, URI finalUri, String html) {
    loaders.put(requested, req -> PageSnapshot.ofUtf8(req, finalUri, html));
    return this;
  }

  FakeArticlePageSession putBytes(URI uri, String html, long byteSize) {
    loaders.put(uri, req -> new PageSnapshot(req, uri, html, byteSize));
    return this;
  }

  FakeArticlePageSession fail(URI uri, PageLoadException exception) {
    failures.put(uri, exception);
    return this;
  }

  @Override
  public PageSnapshot load(URI uri) throws PageLoadException {
    Objects.requireNonNull(uri, "uri must not be null");
    if (failures.containsKey(uri)) {
      throw failures.get(uri);
    }
    Function<URI, PageSnapshot> loader = loaders.get(uri);
    if (loader == null) {
      throw new PageLoadException("No fake page for " + uri);
    }
    return loader.apply(uri);
  }
}
