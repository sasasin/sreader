package net.sasasin.sreader.service;

/** Kind of an expected application-operation failure. */
public enum FailureKind {
  HTTP_STATUS,
  IO,
  INTERRUPTED,
  INVALID_INPUT,
  PARSE,
  RENDER,
  EXTRACTION,
  PERSISTENCE,
  UNEXPECTED
}
