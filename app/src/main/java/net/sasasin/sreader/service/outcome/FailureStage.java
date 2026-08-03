package net.sasasin.sreader.service.outcome;

/** Stage of an application operation where a failure occurred. */
public enum FailureStage {
  RESOLVE_REDIRECT,
  FETCH_FEED,
  PARSE_FEED,
  FETCH_ARTICLE,
  /** Loading a subsequent (or session-scoped) article page during AutoPagerize pagination. */
  FETCH_ARTICLE_PAGE,
  RENDER_ARTICLE,
  /** Resolving or loading the active AutoPagerize rule dataset. */
  LOAD_AUTOPAGERIZE_DATABASE,
  /** Matching or compiling AutoPagerize SITEINFO rules for a URL. */
  MATCH_AUTOPAGERIZE_RULE,
  /** Analyzing pageElement / nextLink / origin policy during pagination. */
  ANALYZE_PAGINATION,
  EXTRACT_TEXT,
  PERSIST_HEADER,
  PERSIST_FULL_TEXT
}
