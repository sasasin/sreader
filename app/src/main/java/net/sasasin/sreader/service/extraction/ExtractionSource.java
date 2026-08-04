package net.sasasin.sreader.service.extraction;

import java.util.Locale;

/** Source that produced extracted full text (or the last attempted source for no-content). */
public enum ExtractionSource {
  FEED,
  XPATH_OVERRIDE,
  CONFIGURED_XPATH,
  READABILITY,
  BODY_TEXT,
  /** AutoPagerize pageElement text (matched rule path). */
  PAGE_ELEMENT,
  /** Multiple pages used different extraction sources. */
  MIXED;

  /** Stable wire value for {@code content_full_text.source_kind}. */
  public String wireValue() {
    return name().toLowerCase(Locale.ROOT);
  }
}
