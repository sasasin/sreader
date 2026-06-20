package net.sasasin.sreader.domain;

public enum FeedStatus {
  ACTIVE("active"),
  UNSUBSCRIBED("unsubscribed");

  private final String value;

  FeedStatus(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static FeedStatus fromValue(String value) {
    for (FeedStatus status : values()) {
      if (status.value.equals(value)) {
        return status;
      }
    }
    throw new IllegalArgumentException("Unsupported feed status: " + value);
  }
}
