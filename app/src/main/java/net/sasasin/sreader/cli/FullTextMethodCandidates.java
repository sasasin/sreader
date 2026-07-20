package net.sasasin.sreader.cli;

import java.util.Iterator;
import net.sasasin.sreader.domain.FullTextMethod;

/** Picocli completion candidates derived from {@link FullTextMethod#wireValues()}. */
public final class FullTextMethodCandidates implements Iterable<String> {

  @Override
  public Iterator<String> iterator() {
    return FullTextMethod.wireValues().iterator();
  }
}
