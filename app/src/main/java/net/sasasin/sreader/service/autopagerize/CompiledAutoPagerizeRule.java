package net.sasasin.sreader.service.autopagerize;

import java.util.Objects;
import java.util.regex.Pattern;

/** Runtime-ready AutoPagerize rule with a compiled URL {@link Pattern}. */
public record CompiledAutoPagerizeRule(
    long datasetId,
    int ordinal,
    int matchOrder,
    String name,
    Pattern urlPattern,
    String urlPatternSource,
    String nextLinkXpath,
    String pageElementXpath,
    String insertBeforeXpath,
    String exampleUrl) {

  public CompiledAutoPagerizeRule {
    Objects.requireNonNull(urlPattern, "urlPattern must not be null");
    Objects.requireNonNull(urlPatternSource, "urlPatternSource must not be null");
    Objects.requireNonNull(nextLinkXpath, "nextLinkXpath must not be null");
    Objects.requireNonNull(pageElementXpath, "pageElementXpath must not be null");
    if (ordinal < 0) {
      throw new IllegalArgumentException("ordinal must be >= 0");
    }
    if (matchOrder < 0) {
      throw new IllegalArgumentException("matchOrder must be >= 0");
    }
    if (urlPatternSource.isBlank()) {
      throw new IllegalArgumentException("urlPatternSource must not be blank");
    }
    if (nextLinkXpath.isBlank()) {
      throw new IllegalArgumentException("nextLinkXpath must not be blank");
    }
    if (pageElementXpath.isBlank()) {
      throw new IllegalArgumentException("pageElementXpath must not be blank");
    }
  }
}
