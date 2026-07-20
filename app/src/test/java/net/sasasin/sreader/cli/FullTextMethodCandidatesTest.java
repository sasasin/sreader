package net.sasasin.sreader.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.sasasin.sreader.domain.FullTextMethod;
import org.junit.jupiter.api.Test;

class FullTextMethodCandidatesTest {

  @Test
  void iteratesCatalogWireValuesInOrder() {
    List<String> values = new ArrayList<>();
    new FullTextMethodCandidates().forEach(values::add);
    assertThat(values).isEqualTo(FullTextMethod.wireValues());
  }
}
