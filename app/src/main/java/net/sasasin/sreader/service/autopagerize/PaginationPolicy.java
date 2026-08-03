package net.sasasin.sreader.service.autopagerize;

import java.time.Duration;
import java.util.Objects;

/** Limits and origin policy for one AutoPagerize pagination run. */
public record PaginationPolicy(
    int maxPages,
    long maxPageBytes,
    long maxTotalBytes,
    Duration totalTimeout,
    boolean sameOriginOnly) {

  public static final int DEFAULT_MAX_PAGES = 20;
  public static final long DEFAULT_MAX_PAGE_BYTES = 5L * 1024 * 1024;
  public static final long DEFAULT_MAX_TOTAL_BYTES = 20L * 1024 * 1024;
  public static final Duration DEFAULT_TOTAL_TIMEOUT = Duration.ofSeconds(120);

  public PaginationPolicy {
    if (maxPages <= 0) {
      throw new IllegalArgumentException("maxPages must be positive");
    }
    if (maxPageBytes <= 0) {
      throw new IllegalArgumentException("maxPageBytes must be positive");
    }
    if (maxTotalBytes <= 0) {
      throw new IllegalArgumentException("maxTotalBytes must be positive");
    }
    if (maxTotalBytes < maxPageBytes) {
      throw new IllegalArgumentException("maxTotalBytes must be >= maxPageBytes");
    }
    Objects.requireNonNull(totalTimeout, "totalTimeout must not be null");
    if (totalTimeout.isZero() || totalTimeout.isNegative()) {
      throw new IllegalArgumentException("totalTimeout must be positive");
    }
  }

  public static PaginationPolicy defaults() {
    return new PaginationPolicy(
        DEFAULT_MAX_PAGES,
        DEFAULT_MAX_PAGE_BYTES,
        DEFAULT_MAX_TOTAL_BYTES,
        DEFAULT_TOTAL_TIMEOUT,
        true);
  }
}
