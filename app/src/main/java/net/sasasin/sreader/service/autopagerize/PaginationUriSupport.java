package net.sasasin.sreader.service.autopagerize;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * URI normalization, origin comparison, and next-link resolution for AutoPagerize pagination.
 * Comparison canonical forms are separate from fetch URIs (fetch URIs are not rewritten).
 */
public final class PaginationUriSupport {

  private PaginationUriSupport() {}

  public static boolean isAllowedScheme(URI uri) {
    Objects.requireNonNull(uri, "uri must not be null");
    String scheme = uri.getScheme();
    return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
  }

  public static boolean hasUserInfo(URI uri) {
    Objects.requireNonNull(uri, "uri must not be null");
    String userInfo = uri.getUserInfo();
    return userInfo != null && !userInfo.isBlank();
  }

  /**
   * Canonical form for visited-set comparison: lower-case host, effective port, normalized path, no
   * fragment. Query is preserved.
   */
  public static URI forVisitedComparison(URI uri) {
    Objects.requireNonNull(uri, "uri must not be null");
    try {
      URI normalized = uri.normalize();
      String scheme = lower(normalized.getScheme());
      String host = lower(normalized.getHost());
      int port = effectivePort(scheme, normalized.getPort());
      String path = normalized.getPath();
      if (path == null || path.isEmpty()) {
        path = "/";
      }
      return new URI(scheme, null, host, port, path, normalized.getQuery(), null);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Cannot canonicalize URI: " + uri, e);
    }
  }

  public static boolean sameOrigin(URI left, URI right) {
    Objects.requireNonNull(left, "left must not be null");
    Objects.requireNonNull(right, "right must not be null");
    if (!Objects.equals(lower(left.getScheme()), lower(right.getScheme()))) {
      return false;
    }
    if (!Objects.equals(lower(left.getHost()), lower(right.getHost()))) {
      return false;
    }
    return effectivePort(left.getScheme(), left.getPort())
        == effectivePort(right.getScheme(), right.getPort());
  }

  public static int effectivePort(String scheme, int port) {
    if (port >= 0) {
      return port;
    }
    if ("http".equalsIgnoreCase(scheme)) {
      return 80;
    }
    if ("https".equalsIgnoreCase(scheme)) {
      return 443;
    }
    return -1;
  }

  /**
   * Resolves a next-link candidate against {@code <base href>} when present, otherwise against the
   * page final URI.
   */
  public static Optional<URI> resolveNextCandidate(
      URI pageFinalUri, Document document, String raw) {
    Objects.requireNonNull(pageFinalUri, "pageFinalUri must not be null");
    Objects.requireNonNull(document, "document must not be null");
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(resolveDocumentBase(pageFinalUri, document).resolve(raw.trim()));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  public static URI resolveDocumentBase(URI pageFinalUri, Document document) {
    Objects.requireNonNull(pageFinalUri, "pageFinalUri must not be null");
    Objects.requireNonNull(document, "document must not be null");
    Element base = document.selectFirst("base[href]");
    if (base == null) {
      return pageFinalUri;
    }
    String href = base.attr("href");
    if (href.isBlank()) {
      return pageFinalUri;
    }
    try {
      return pageFinalUri.resolve(href.trim());
    } catch (IllegalArgumentException e) {
      return pageFinalUri;
    }
  }

  public static Optional<String> firstNonBlankAttribute(Element element) {
    Objects.requireNonNull(element, "element must not be null");
    for (String attr : new String[] {"href", "action", "value"}) {
      String value = element.attr(attr);
      if (!value.isBlank()) {
        return Optional.of(value.trim());
      }
    }
    return Optional.empty();
  }

  private static String lower(String value) {
    return value == null ? null : value.toLowerCase(Locale.ROOT);
  }
}
