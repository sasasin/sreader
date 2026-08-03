package net.sasasin.sreader.service.extraction;

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
  MIXED
}
