package net.sasasin.sreader.service;

/** Why extraction fell back from a preferred strategy to body text. */
public enum ExtractionFallbackReason {
  CONFIGURED_XPATH_INVALID,
  CONFIGURED_XPATH_NO_MATCH,
  CONFIGURED_XPATH_EMPTY,
  READABILITY_FAILED,
  READABILITY_EMPTY
}
