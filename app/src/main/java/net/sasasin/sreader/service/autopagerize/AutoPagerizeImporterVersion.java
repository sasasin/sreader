package net.sasasin.sreader.service.autopagerize;

/**
 * Explicit importer algorithm version stored with each dataset identity ({@code format}, {@code
 * source_sha256}, {@code importer_version}). Bump when parse/validation semantics change.
 */
public final class AutoPagerizeImporterVersion {

  /** Current import pipeline version. */
  public static final int CURRENT = 1;

  private AutoPagerizeImporterVersion() {}
}
