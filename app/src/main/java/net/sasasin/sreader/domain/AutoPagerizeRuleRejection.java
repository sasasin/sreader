package net.sasasin.sreader.domain;

import java.util.Objects;

/**
 * Import-time rejected AutoPagerize item. {@code errorsJson} is a structured JSON array or object,
 * not a free-form concatenated string.
 */
public record AutoPagerizeRuleRejection(
    long datasetId, int ordinal, String name, String rawItemJson, String errorsJson) {

  public AutoPagerizeRuleRejection {
    Objects.requireNonNull(rawItemJson, "rawItemJson must not be null");
    Objects.requireNonNull(errorsJson, "errorsJson must not be null");
    if (ordinal < 0) {
      throw new IllegalArgumentException("ordinal must be >= 0");
    }
    if (rawItemJson.isBlank()) {
      throw new IllegalArgumentException("rawItemJson must not be blank");
    }
    if (errorsJson.isBlank()) {
      throw new IllegalArgumentException("errorsJson must not be blank");
    }
    if (!isJsonArrayOrObject(errorsJson)) {
      throw new IllegalArgumentException("errorsJson must be a JSON array or object");
    }
  }

  private static boolean isJsonArrayOrObject(String json) {
    String trimmed = json.trim();
    return (trimmed.startsWith("[") && trimmed.endsWith("]"))
        || (trimmed.startsWith("{") && trimmed.endsWith("}"));
  }
}
