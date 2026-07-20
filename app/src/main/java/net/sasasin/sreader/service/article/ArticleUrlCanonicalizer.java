package net.sasasin.sreader.service.article;

import java.net.URI;
import java.util.Arrays;
import org.springframework.stereotype.Component;

@Component
public class ArticleUrlCanonicalizer {

  private static final String COMEMO_HOST = "comemo.nikkei.com";
  private static final String COMEMO_ARTICLE_PATH_PREFIX = "/n/";

  private final String canonicalizableHost;
  private final String canonicalizablePathPrefix;

  public ArticleUrlCanonicalizer() {
    this(COMEMO_HOST, COMEMO_ARTICLE_PATH_PREFIX);
  }

  ArticleUrlCanonicalizer(String canonicalizableHost, String canonicalizablePathPrefix) {
    this.canonicalizableHost = canonicalizableHost;
    this.canonicalizablePathPrefix = canonicalizablePathPrefix;
  }

  public URI canonicalize(URI fetchUri) {
    URI normalized = fetchUri.normalize();
    String rawQuery = normalized.getRawQuery();
    if (isComemoArticle(normalized) && rawQuery != null) {
      rawQuery =
          Arrays.stream(rawQuery.split("&", -1))
              .filter(parameter -> !parameterName(parameter).equals("gs"))
              .reduce((left, right) -> left + "&" + right)
              .orElse(null);
    }
    if (!isComemoArticle(normalized)) {
      return URI.create(normalized.getScheme() + ":" + normalized.getRawSchemeSpecificPart());
    }
    return URI.create(
        normalized.getScheme()
            + "://"
            + normalized.getRawAuthority()
            + normalized.getRawPath()
            + (rawQuery == null ? "" : "?" + rawQuery));
  }

  private boolean isComemoArticle(URI uri) {
    return uri.getHost() != null
        && uri.getHost().equalsIgnoreCase(canonicalizableHost)
        && uri.getPath() != null
        && uri.getPath().startsWith(canonicalizablePathPrefix);
  }

  private String parameterName(String parameter) {
    int separator = parameter.indexOf('=');
    return separator < 0 ? parameter : parameter.substring(0, separator);
  }
}
