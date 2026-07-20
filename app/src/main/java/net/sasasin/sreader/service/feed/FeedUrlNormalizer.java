package net.sasasin.sreader.service.feed;

import java.net.URI;

public final class FeedUrlNormalizer {

  private FeedUrlNormalizer() {}

  public static String normalizeSeedLine(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
      return null;
    }
    String url = trimmed.split("\\t")[0].trim();
    try {
      return normalizeStrict(url);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public static String normalizeStrict(String value) {
    if (value == null) {
      throw new IllegalArgumentException("url is required");
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("url must not be blank");
    }
    URI uri;
    try {
      uri = URI.create(trimmed);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("url must be a valid absolute URI: " + trimmed, e);
    }
    if (!uri.isAbsolute() || uri.getHost() == null) {
      throw new IllegalArgumentException("url must be an absolute http or https URI: " + trimmed);
    }
    String scheme = uri.getScheme();
    if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
      throw new IllegalArgumentException("url scheme must be http or https: " + trimmed);
    }
    if (uri.getUserInfo() != null) {
      throw new IllegalArgumentException("url must not include userinfo: " + trimmed);
    }
    return uri.normalize().toString();
  }
}
