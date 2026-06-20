package net.sasasin.sreader.repository;

import static net.sasasin.sreader.jooq.Tables.EFT_RULES;

import java.util.List;
import net.sasasin.sreader.domain.ExtractRule;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class ExtractRuleRepository {

  private final DSLContext dsl;

  public ExtractRuleRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public List<ExtractRule> findAll() {
    return dsl.select(EFT_RULES.ID, EFT_RULES.URL_PATTERN, EFT_RULES.EXTRACT_RULE)
        .from(EFT_RULES)
        .fetch(
            record ->
                new ExtractRule(
                    record.get(EFT_RULES.ID),
                    record.get(EFT_RULES.URL_PATTERN),
                    record.get(EFT_RULES.EXTRACT_RULE)));
  }
}
