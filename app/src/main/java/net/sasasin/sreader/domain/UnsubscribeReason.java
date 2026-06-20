package net.sasasin.sreader.domain;

public enum UnsubscribeReason {
  NOT_INTERESTED("not_interested"),
  SITE_CLOSED("site_closed"),
  FEED_DEAD("feed_dead"),
  MOVED("moved"),
  OTHER("other");

  private final String value;

  UnsubscribeReason(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static UnsubscribeReason fromValue(String value) {
    for (UnsubscribeReason reason : values()) {
      if (reason.value.equals(value)) {
        return reason;
      }
    }
    throw new IllegalArgumentException("Unsupported unsubscribe reason: " + value);
  }
}
