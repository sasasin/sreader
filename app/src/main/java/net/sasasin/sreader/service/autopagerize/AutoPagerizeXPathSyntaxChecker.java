package net.sasasin.sreader.service.autopagerize;

import java.util.Objects;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/**
 * Validates XPath syntax via the same jsoup {@link Document#selectXpath(String)} path used at
 * runtime. Empty selection is valid; only syntax / evaluation exceptions fail.
 */
@Component
public class AutoPagerizeXPathSyntaxChecker {

  private static final Document EMPTY_DOCUMENT = Jsoup.parse("<html><body></body></html>");

  /**
   * @return empty when syntax is acceptable; otherwise a short diagnostic message
   */
  public String validateSyntax(String xpath) {
    Objects.requireNonNull(xpath, "xpath must not be null");
    try {
      EMPTY_DOCUMENT.selectXpath(xpath);
      return null;
    } catch (RuntimeException e) {
      String message = e.getMessage();
      if (message == null || message.isBlank()) {
        return e.getClass().getSimpleName();
      }
      return message;
    }
  }

  public boolean isSyntacticallyValid(String xpath) {
    return validateSyntax(xpath) == null;
  }
}
