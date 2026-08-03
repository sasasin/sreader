package net.sasasin.sreader.service.autopagerize;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.sasasin.sreader.domain.AutoPagerizeRule;
import org.junit.jupiter.api.Test;

class AutoPagerizeMatchOrderAssignerTest {

  @Test
  void ordersByUrlPatternLengthDescThenOrdinalAsc() {
    AutoPagerizeParsedItem shortPattern = accepted(0, "^https://a/", "A");
    AutoPagerizeParsedItem longPattern = accepted(1, "^https://example.com/articles/", "B");
    AutoPagerizeParsedItem sameLengthEarlier = accepted(2, "^https://bbbb/", "C");
    AutoPagerizeParsedItem sameLengthLater = accepted(3, "^https://cccc/", "D");

    List<AutoPagerizeRule> rules =
        AutoPagerizeMatchOrderAssigner.assign(
            9L, List.of(shortPattern, longPattern, sameLengthEarlier, sameLengthLater));

    assertThat(rules).extracting(AutoPagerizeRule::name).containsExactly("B", "C", "D", "A");
    assertThat(rules).extracting(AutoPagerizeRule::matchOrder).containsExactly(0, 1, 2, 3);
    assertThat(rules).extracting(AutoPagerizeRule::ordinal).containsExactly(1, 2, 3, 0);
    assertThat(rules).allMatch(rule -> rule.datasetId() == 9L);
  }

  private static AutoPagerizeParsedItem accepted(int ordinal, String urlPattern, String name) {
    return new AutoPagerizeParsedItem(
        ordinal,
        null,
        null,
        name,
        null,
        null,
        null,
        urlPattern,
        "//a",
        "//div",
        null,
        null,
        "{\"name\":\"" + name + "\"}",
        List.of(),
        List.of());
  }
}
