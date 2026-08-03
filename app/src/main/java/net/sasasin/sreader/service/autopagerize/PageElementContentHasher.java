package net.sasasin.sreader.service.autopagerize;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * SHA-256 of AutoPagerize pageElement outer HTML (document order concatenation). Outer HTML is used
 * rather than text so pages with identical text but different markup are not treated as content
 * loops.
 */
public final class PageElementContentHasher {

  private PageElementContentHasher() {}

  public static String sha256Hex(String pageElementOuterHtml) {
    Objects.requireNonNull(pageElementOuterHtml, "pageElementOuterHtml must not be null");
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(pageElementOuterHtml.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        hex.append(String.format("%02x", b & 0xff));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by the JDK", e);
    }
  }
}
