package net.sasasin.sreader.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import org.springframework.stereotype.Component;

@Component
public class ArticleUrlCanonicalizer {

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
    try {
      return new URI(
          normalized.getScheme(),
          normalized.getRawAuthority(),
          normalized.getRawPath(),
          rawQuery,
          null);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Unable to canonicalize URI: " + fetchUri, e);
    }
  }

  private boolean isComemoArticle(URI uri) {
    return uri.getHost() != null
        && uri.getHost().equalsIgnoreCase("comemo.nikkei.com")
        && uri.getPath() != null
        && uri.getPath().startsWith("/n/");
  }

  private String parameterName(String parameter) {
    int separator = parameter.indexOf('=');
    return separator < 0 ? parameter : parameter.substring(0, separator);
  }
}
