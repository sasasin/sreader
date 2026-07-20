package net.sasasin.sreader.service;

/** Outcome of inserting a content header or refreshing fetch URL for an existing one. */
public enum ContentHeaderUpsertOutcome {
  INSERTED,
  EXISTING_REFRESHED
}
