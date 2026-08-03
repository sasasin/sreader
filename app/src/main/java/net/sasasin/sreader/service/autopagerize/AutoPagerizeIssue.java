package net.sasasin.sreader.service.autopagerize;

import java.util.Objects;

/** Structured validation issue for a single SITEINFO item. */
public record AutoPagerizeIssue(String code, String message) {

  public AutoPagerizeIssue {
    Objects.requireNonNull(code, "code must not be null");
    Objects.requireNonNull(message, "message must not be null");
    if (code.isBlank()) {
      throw new IllegalArgumentException("code must not be blank");
    }
    if (message.isBlank()) {
      throw new IllegalArgumentException("message must not be blank");
    }
  }
}
