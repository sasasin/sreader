package net.sasasin.sreader.service.extraction.browser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.net.URI;
import org.junit.jupiter.api.Test;

class RenderedPageTest {

  @Test
  void acceptsBlankHtml() {
    RenderedPage page = new RenderedPage(URI.create("https://example.test/"), "");
    assertThat(page.finalUri()).isEqualTo(URI.create("https://example.test/"));
    assertThat(page.html()).isEmpty();
  }

  @Test
  void rejectsNullFinalUri() {
    assertThatNullPointerException()
        .isThrownBy(() -> new RenderedPage(null, "html"))
        .withMessageContaining("finalUri");
  }

  @Test
  void rejectsNullHtml() {
    assertThatNullPointerException()
        .isThrownBy(() -> new RenderedPage(URI.create("https://example.test/"), null))
        .withMessageContaining("html");
  }
}
