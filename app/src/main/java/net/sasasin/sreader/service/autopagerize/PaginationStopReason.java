package net.sasasin.sreader.service.autopagerize;

/** Why AutoPagerize pagination stopped. Success and failure reasons share one enum. */
public enum PaginationStopReason {
  /** No SITEINFO rule matched; single-page fallback is appropriate. */
  NO_MATCHING_RULE,
  /** Matched rule chain ended because the current page has no next link. */
  NO_NEXT_LINK,

  PAGE_ELEMENT_MISSING,
  INVALID_NEXT_URI,
  UNSUPPORTED_SCHEME,
  OFF_ORIGIN,
  REDIRECT_OFF_ORIGIN,
  URL_LOOP,
  CONTENT_LOOP,
  MAX_PAGES,
  MAX_PAGE_BYTES,
  MAX_TOTAL_BYTES,
  TIMEOUT,
  FETCH_FAILED,
  INTERRUPTED;

  public boolean isSuccess() {
    return this == NO_MATCHING_RULE || this == NO_NEXT_LINK;
  }
}
