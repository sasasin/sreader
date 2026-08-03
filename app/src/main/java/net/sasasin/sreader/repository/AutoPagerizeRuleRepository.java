package net.sasasin.sreader.repository;

import static net.sasasin.sreader.jooq.Tables.AUTOPAGERIZE_RULE;
import static net.sasasin.sreader.jooq.Tables.AUTOPAGERIZE_RULE_REJECTION;

import java.util.List;
import net.sasasin.sreader.domain.AutoPagerizeRule;
import net.sasasin.sreader.domain.AutoPagerizeRuleCounts;
import net.sasasin.sreader.domain.AutoPagerizeRuleRejection;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/**
 * Persistence for accepted AutoPagerize rules and import rejections under a dataset. Batch inserts
 * are intended to run inside the import service transaction together with dataset creation.
 */
@Repository
public class AutoPagerizeRuleRepository {

  private final DSLContext dsl;

  public AutoPagerizeRuleRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public void insertRules(List<AutoPagerizeRule> rules) {
    if (rules == null || rules.isEmpty()) {
      return;
    }
    var insert =
        dsl.insertInto(
            AUTOPAGERIZE_RULE,
            AUTOPAGERIZE_RULE.DATASET_ID,
            AUTOPAGERIZE_RULE.ORDINAL,
            AUTOPAGERIZE_RULE.MATCH_ORDER,
            AUTOPAGERIZE_RULE.EXTERNAL_ID,
            AUTOPAGERIZE_RULE.RESOURCE_URL,
            AUTOPAGERIZE_RULE.NAME,
            AUTOPAGERIZE_RULE.CREATED_BY,
            AUTOPAGERIZE_RULE.SOURCE_CREATED_AT,
            AUTOPAGERIZE_RULE.SOURCE_UPDATED_AT,
            AUTOPAGERIZE_RULE.URL_PATTERN,
            AUTOPAGERIZE_RULE.NEXT_LINK_XPATH,
            AUTOPAGERIZE_RULE.PAGE_ELEMENT_XPATH,
            AUTOPAGERIZE_RULE.INSERT_BEFORE_XPATH,
            AUTOPAGERIZE_RULE.EXAMPLE_URL,
            AUTOPAGERIZE_RULE.RAW_ITEM);
    for (AutoPagerizeRule rule : rules) {
      insert =
          insert.values(
              rule.datasetId(),
              rule.ordinal(),
              rule.matchOrder(),
              rule.externalId(),
              rule.resourceUrl(),
              rule.name(),
              rule.createdBy(),
              rule.sourceCreatedAt(),
              rule.sourceUpdatedAt(),
              rule.urlPattern(),
              rule.nextLinkXpath(),
              rule.pageElementXpath(),
              rule.insertBeforeXpath(),
              rule.exampleUrl(),
              toJsonb(rule.rawItemJson()));
    }
    insert.execute();
  }

  public void insertRejections(List<AutoPagerizeRuleRejection> rejections) {
    if (rejections == null || rejections.isEmpty()) {
      return;
    }
    var insert =
        dsl.insertInto(
            AUTOPAGERIZE_RULE_REJECTION,
            AUTOPAGERIZE_RULE_REJECTION.DATASET_ID,
            AUTOPAGERIZE_RULE_REJECTION.ORDINAL,
            AUTOPAGERIZE_RULE_REJECTION.NAME,
            AUTOPAGERIZE_RULE_REJECTION.RAW_ITEM,
            AUTOPAGERIZE_RULE_REJECTION.ERRORS);
    for (AutoPagerizeRuleRejection rejection : rejections) {
      insert =
          insert.values(
              rejection.datasetId(),
              rejection.ordinal(),
              rejection.name(),
              toJsonb(rejection.rawItemJson()),
              toJsonb(rejection.errorsJson()));
    }
    insert.execute();
  }

  public List<AutoPagerizeRule> findRulesByDatasetIdOrderedByMatchOrder(long datasetId) {
    return dsl.selectFrom(AUTOPAGERIZE_RULE)
        .where(AUTOPAGERIZE_RULE.DATASET_ID.eq(datasetId))
        .orderBy(AUTOPAGERIZE_RULE.MATCH_ORDER.asc())
        .fetch(this::mapRule);
  }

  public List<AutoPagerizeRuleRejection> findRejectionsByDatasetIdOrderedByOrdinal(long datasetId) {
    return dsl.selectFrom(AUTOPAGERIZE_RULE_REJECTION)
        .where(AUTOPAGERIZE_RULE_REJECTION.DATASET_ID.eq(datasetId))
        .orderBy(AUTOPAGERIZE_RULE_REJECTION.ORDINAL.asc())
        .fetch(this::mapRejection);
  }

  public AutoPagerizeRuleCounts countByDatasetId(long datasetId) {
    int accepted =
        dsl.fetchCount(
            dsl.selectFrom(AUTOPAGERIZE_RULE).where(AUTOPAGERIZE_RULE.DATASET_ID.eq(datasetId)));
    int rejected =
        dsl.fetchCount(
            dsl.selectFrom(AUTOPAGERIZE_RULE_REJECTION)
                .where(AUTOPAGERIZE_RULE_REJECTION.DATASET_ID.eq(datasetId)));
    return new AutoPagerizeRuleCounts(accepted, rejected);
  }

  private AutoPagerizeRule mapRule(Record record) {
    return new AutoPagerizeRule(
        record.get(AUTOPAGERIZE_RULE.DATASET_ID),
        record.get(AUTOPAGERIZE_RULE.ORDINAL),
        record.get(AUTOPAGERIZE_RULE.MATCH_ORDER),
        record.get(AUTOPAGERIZE_RULE.EXTERNAL_ID),
        record.get(AUTOPAGERIZE_RULE.RESOURCE_URL),
        record.get(AUTOPAGERIZE_RULE.NAME),
        record.get(AUTOPAGERIZE_RULE.CREATED_BY),
        record.get(AUTOPAGERIZE_RULE.SOURCE_CREATED_AT),
        record.get(AUTOPAGERIZE_RULE.SOURCE_UPDATED_AT),
        record.get(AUTOPAGERIZE_RULE.URL_PATTERN),
        record.get(AUTOPAGERIZE_RULE.NEXT_LINK_XPATH),
        record.get(AUTOPAGERIZE_RULE.PAGE_ELEMENT_XPATH),
        record.get(AUTOPAGERIZE_RULE.INSERT_BEFORE_XPATH),
        record.get(AUTOPAGERIZE_RULE.EXAMPLE_URL),
        record.get(AUTOPAGERIZE_RULE.RAW_ITEM).data());
  }

  private AutoPagerizeRuleRejection mapRejection(Record record) {
    return new AutoPagerizeRuleRejection(
        record.get(AUTOPAGERIZE_RULE_REJECTION.DATASET_ID),
        record.get(AUTOPAGERIZE_RULE_REJECTION.ORDINAL),
        record.get(AUTOPAGERIZE_RULE_REJECTION.NAME),
        record.get(AUTOPAGERIZE_RULE_REJECTION.RAW_ITEM).data(),
        record.get(AUTOPAGERIZE_RULE_REJECTION.ERRORS).data());
  }

  private static JSONB toJsonb(String json) {
    return JSONB.valueOf(json);
  }
}
