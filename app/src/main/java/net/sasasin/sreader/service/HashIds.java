package net.sasasin.sreader.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class HashIds {

  private HashIds() {}

  static String md5(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] hash = md.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        result.append(String.format("%02x", b & 0xff));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("MD5 is required by the JDK", e);
    }
  }
}
