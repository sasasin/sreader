package net.sasasin.sreader.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Canonical catalog for full-text extraction methods.
 *
 * <p>Each constant holds its wire value (DB / TOML / CLI) and a sealed {@link Definition} that
 * describes runtime extraction behavior. Boundaries that persist or parse methods convert only via
 * {@link #value()} and {@link #fromValue(String)}.
 *
 * <p>Adding a wire value also requires updating the database check constraint and user-facing
 * documentation.
 */
public enum FullTextMethod {
  FEED("feed", new Definition.FeedEntry()),

  HTTP("http", new Definition.HttpArticle(PaginationMode.NONE, HtmlExtractor.XPATH_OR_BODY_TEXT)),

  HTTP_READABILITY(
      "http_readability",
      new Definition.HttpArticle(PaginationMode.NONE, HtmlExtractor.READABILITY)),

  HTTP_AUTOPAGERIZE(
      "http_autopagerize",
      new Definition.HttpArticle(PaginationMode.AUTOPAGERIZE, HtmlExtractor.XPATH_OR_BODY_TEXT)),

  HTTP_AUTOPAGERIZE_READABILITY(
      "http_autopagerize_readability",
      new Definition.HttpArticle(PaginationMode.AUTOPAGERIZE, HtmlExtractor.READABILITY)),

  PLAYWRIGHT(
      "playwright",
      new Definition.PlaywrightArticle(
          PlaywrightMode.STANDARD, PaginationMode.NONE, HtmlExtractor.XPATH_OR_BODY_TEXT)),

  PLAYWRIGHT_READABILITY(
      "playwright_readability",
      new Definition.PlaywrightArticle(
          PlaywrightMode.STANDARD, PaginationMode.NONE, HtmlExtractor.READABILITY)),

  PLAYWRIGHT_AUTOPAGERIZE(
      "playwright_autopagerize",
      new Definition.PlaywrightArticle(
          PlaywrightMode.STANDARD, PaginationMode.AUTOPAGERIZE, HtmlExtractor.XPATH_OR_BODY_TEXT)),

  PLAYWRIGHT_AUTOPAGERIZE_READABILITY(
      "playwright_autopagerize_readability",
      new Definition.PlaywrightArticle(
          PlaywrightMode.STANDARD, PaginationMode.AUTOPAGERIZE, HtmlExtractor.READABILITY)),

  PLAYWRIGHT_INFY_SCROLL(
      "playwright_infy_scroll",
      new Definition.PlaywrightArticle(
          PlaywrightMode.INFY_SCROLL, PaginationMode.NONE, HtmlExtractor.XPATH_OR_BODY_TEXT)),

  PLAYWRIGHT_INFY_SCROLL_READABILITY(
      "playwright_infy_scroll_readability",
      new Definition.PlaywrightArticle(
          PlaywrightMode.INFY_SCROLL, PaginationMode.NONE, HtmlExtractor.READABILITY));

  private static final FullTextMethod DEFAULT = HTTP;

  private static final Map<String, FullTextMethod> BY_VALUE =
      Arrays.stream(values())
          .collect(
              Collectors.toUnmodifiableMap(
                  FullTextMethod::value,
                  Function.identity(),
                  (left, right) -> {
                    throw new IllegalStateException(
                        "Duplicate full text method wire value: " + left.value());
                  }));

  private static final List<String> WIRE_VALUES =
      Arrays.stream(values()).map(FullTextMethod::value).toList();

  private final String value;
  private final Definition definition;

  FullTextMethod(String value, Definition definition) {
    this.value = Objects.requireNonNull(value, "wire value must not be null");
    if (this.value.isBlank()) {
      throw new IllegalArgumentException("wire value must not be blank");
    }
    this.definition = Objects.requireNonNull(definition, "definition must not be null");
  }

  public String value() {
    return value;
  }

  public Definition definition() {
    return definition;
  }

  /**
   * Default method used when TOML omits {@code full_text_method}, when DB null compatibility
   * mapping applies, and when extraction is invoked without an explicit method.
   */
  public static FullTextMethod defaultMethod() {
    return DEFAULT;
  }

  /** Immutable wire values in enum declaration order. */
  public static List<String> wireValues() {
    return WIRE_VALUES;
  }

  /** Comma-separated wire values for CLI / validation error messages. */
  public static String supportedValues() {
    return String.join(", ", WIRE_VALUES);
  }

  public static FullTextMethod fromValue(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Unsupported full text method: null");
    }
    FullTextMethod method = BY_VALUE.get(value);
    if (method == null) {
      throw new IllegalArgumentException("Unsupported full text method: " + value);
    }
    return method;
  }

  /** Whether this method uses feed entry body content (no article fetch). */
  public boolean usesFeedEntryContent() {
    return definition instanceof Definition.FeedEntry;
  }

  /** Whether an entry link is required for feed-entry probe / article URL resolution. */
  public boolean requiresEntryLink() {
    return !usesFeedEntryContent();
  }

  /** Whether article URL probing is supported. */
  public boolean supportsArticleProbe() {
    return !usesFeedEntryContent();
  }

  /** Whether an explicit XPath override is applicable. */
  public boolean supportsXpathOverride() {
    return !usesFeedEntryContent();
  }

  /** Whether Playwright rendering is required. */
  public boolean requiresPlaywright() {
    return definition instanceof Definition.PlaywrightArticle;
  }

  /** Whether AutoPagerize multi-page tracking is required. */
  public boolean usesAutopagerize() {
    return switch (definition) {
      case Definition.HttpArticle http -> http.pagination() == PaginationMode.AUTOPAGERIZE;
      case Definition.PlaywrightArticle playwright ->
          playwright.pagination() == PaginationMode.AUTOPAGERIZE;
      case Definition.FeedEntry ignored -> false;
    };
  }

  public Optional<Definition.ArticleDefinition> articleDefinition() {
    return definition instanceof Definition.ArticleDefinition article
        ? Optional.of(article)
        : Optional.empty();
  }

  /** HTML extractor applied to a fetched or rendered article document. */
  public enum HtmlExtractor {
    XPATH_OR_BODY_TEXT,
    READABILITY
  }

  /** Pagination strategy for article methods. */
  public enum PaginationMode {
    NONE,
    AUTOPAGERIZE
  }

  /** Playwright page rendering mode (catalog-owned; browser adapters depend on this). */
  public enum PlaywrightMode {
    STANDARD,
    INFY_SCROLL
  }

  /**
   * Sealed runtime definition for a full-text method. Invalid combinations such as FEED +
   * READABILITY or HTTP + Infy cannot be constructed.
   */
  public sealed interface Definition permits Definition.FeedEntry, Definition.ArticleDefinition {

    /** Feed entry body content; no article document fetch. */
    record FeedEntry() implements Definition {}

    /** Article methods that apply an {@link HtmlExtractor} to an HTML document. */
    sealed interface ArticleDefinition extends Definition permits HttpArticle, PlaywrightArticle {
      HtmlExtractor extractor();
    }

    /**
     * HTTP fetch of the article URL, then HTML extraction. {@link PaginationMode#AUTOPAGERIZE}
     * tracks multi-page chains through a cookie-isolated session.
     */
    record HttpArticle(PaginationMode pagination, HtmlExtractor extractor)
        implements ArticleDefinition {
      public HttpArticle {
        Objects.requireNonNull(pagination, "pagination must not be null");
        Objects.requireNonNull(extractor, "extractor must not be null");
      }
    }

    /**
     * Playwright render of the article URL with a mode, then HTML extraction. {@link
     * PaginationMode#AUTOPAGERIZE} tracks multi-page chains through a short-lived standard browser
     * context (no extension / persistent profile). Infy Scroll cannot combine with AutoPagerize.
     */
    record PlaywrightArticle(
        PlaywrightMode mode, PaginationMode pagination, HtmlExtractor extractor)
        implements ArticleDefinition {
      public PlaywrightArticle {
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(pagination, "pagination must not be null");
        Objects.requireNonNull(extractor, "extractor must not be null");
        if (mode == PlaywrightMode.INFY_SCROLL && pagination == PaginationMode.AUTOPAGERIZE) {
          throw new IllegalArgumentException(
              "Infy Scroll cannot combine with AutoPagerize pagination");
        }
      }
    }
  }
}
