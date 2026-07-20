package net.sasasin.sreader.service;

/** Why text extraction completed without usable content. */
public enum NoContentReason {
  FEED_CONTENT_MISSING,
  XPATH_NO_MATCH,
  XPATH_MATCHED_EMPTY,
  BODY_TEXT_EMPTY
}
