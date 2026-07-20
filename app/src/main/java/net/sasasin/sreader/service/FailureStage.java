package net.sasasin.sreader.service;

/** Stage of an application operation where a failure occurred. */
public enum FailureStage {
  RESOLVE_REDIRECT,
  FETCH_FEED,
  PARSE_FEED,
  FETCH_ARTICLE,
  RENDER_ARTICLE,
  EXTRACT_TEXT,
  PERSIST_HEADER,
  PERSIST_FULL_TEXT
}
