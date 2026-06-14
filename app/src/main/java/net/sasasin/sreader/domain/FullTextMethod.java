package net.sasasin.sreader.domain;

public enum FullTextMethod {
	HTTP("http"),
	FEED("feed"),
	PLAYWRIGHT("playwright"),
	PLAYWRIGHT_READABILITY("playwright_readability"),
	PLAYWRIGHT_INFY_SCROLL("playwright_infy_scroll"),
	PLAYWRIGHT_INFY_SCROLL_READABILITY("playwright_infy_scroll_readability");

	private final String value;

	FullTextMethod(String value) {
		this.value = value;
	}

	public String value() {
		return value;
	}

	public static FullTextMethod fromValue(String value) {
		for (FullTextMethod method : values()) {
			if (method.value.equals(value)) {
				return method;
			}
		}
		throw new IllegalArgumentException("Unsupported full text method: " + value);
	}
}
