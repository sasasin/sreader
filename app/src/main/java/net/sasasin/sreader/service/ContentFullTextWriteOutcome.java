package net.sasasin.sreader.service;

/** Outcome of attempting to persist full text when absent. */
public enum ContentFullTextWriteOutcome {
  INSERTED,
  ALREADY_EXISTS,
  NO_CONTENT
}
