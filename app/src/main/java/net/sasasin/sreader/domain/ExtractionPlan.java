package net.sasasin.sreader.domain;

public record ExtractionPlan(
    SourceKind sourceKind, boolean useInfyScroll, ExtractorKind extractorKind) {

  public enum SourceKind {
    FEED,
    HTTP,
    PLAYWRIGHT
  }

  public enum ExtractorKind {
    XPATH_OR_BODY_TEXT,
    READABILITY
  }

  public static ExtractionPlan from(FullTextMethod method) {
    return switch (method) {
      case FEED -> new ExtractionPlan(SourceKind.FEED, false, ExtractorKind.XPATH_OR_BODY_TEXT);
      case HTTP -> new ExtractionPlan(SourceKind.HTTP, false, ExtractorKind.XPATH_OR_BODY_TEXT);
      case PLAYWRIGHT ->
          new ExtractionPlan(SourceKind.PLAYWRIGHT, false, ExtractorKind.XPATH_OR_BODY_TEXT);
      case PLAYWRIGHT_READABILITY ->
          new ExtractionPlan(SourceKind.PLAYWRIGHT, false, ExtractorKind.READABILITY);
      case PLAYWRIGHT_INFY_SCROLL ->
          new ExtractionPlan(SourceKind.PLAYWRIGHT, true, ExtractorKind.XPATH_OR_BODY_TEXT);
      case PLAYWRIGHT_INFY_SCROLL_READABILITY ->
          new ExtractionPlan(SourceKind.PLAYWRIGHT, true, ExtractorKind.READABILITY);
    };
  }
}
