package net.sasasin.sreader.repository;

import static net.sasasin.sreader.jooq.Tables.AUTOPAGERIZE_DATASET;

import java.util.List;
import java.util.Optional;
import net.sasasin.sreader.domain.AutoPagerizeDataset;
import net.sasasin.sreader.domain.AutoPagerizeDatasetCreate;
import net.sasasin.sreader.domain.AutoPagerizeDatasetSummary;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/**
 * Persistence for immutable AutoPagerize dataset metadata. Transaction boundaries belong to the
 * import service that may also write rules, rejections, and active state in one unit of work.
 */
@Repository
public class AutoPagerizeDatasetRepository {

  private final DSLContext dsl;

  public AutoPagerizeDatasetRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public Optional<AutoPagerizeDataset> findByIdentity(
      String format, String sourceSha256, int importerVersion) {
    return dsl.selectFrom(AUTOPAGERIZE_DATASET)
        .where(AUTOPAGERIZE_DATASET.FORMAT.eq(format))
        .and(AUTOPAGERIZE_DATASET.SOURCE_SHA256.eq(sourceSha256))
        .and(AUTOPAGERIZE_DATASET.IMPORTER_VERSION.eq(importerVersion))
        .fetchOptional(this::mapDataset);
  }

  public Optional<AutoPagerizeDataset> findById(long id) {
    return dsl.selectFrom(AUTOPAGERIZE_DATASET)
        .where(AUTOPAGERIZE_DATASET.ID.eq(id))
        .fetchOptional(this::mapDataset);
  }

  public boolean existsById(long id) {
    return dsl.fetchExists(
        dsl.selectOne().from(AUTOPAGERIZE_DATASET).where(AUTOPAGERIZE_DATASET.ID.eq(id)));
  }

  public List<AutoPagerizeDatasetSummary> listNewestFirst() {
    return dsl.select(
            AUTOPAGERIZE_DATASET.ID,
            AUTOPAGERIZE_DATASET.FORMAT,
            AUTOPAGERIZE_DATASET.SOURCE_FILENAME,
            AUTOPAGERIZE_DATASET.SOURCE_URI,
            AUTOPAGERIZE_DATASET.SOURCE_SHA256,
            AUTOPAGERIZE_DATASET.IMPORTER_VERSION,
            AUTOPAGERIZE_DATASET.IMPORTED_AT,
            AUTOPAGERIZE_DATASET.INPUT_ITEM_COUNT,
            AUTOPAGERIZE_DATASET.ACCEPTED_RULE_COUNT,
            AUTOPAGERIZE_DATASET.REJECTED_RULE_COUNT)
        .from(AUTOPAGERIZE_DATASET)
        .orderBy(AUTOPAGERIZE_DATASET.IMPORTED_AT.desc(), AUTOPAGERIZE_DATASET.ID.desc())
        .fetch(
            record ->
                new AutoPagerizeDatasetSummary(
                    record.get(AUTOPAGERIZE_DATASET.ID),
                    record.get(AUTOPAGERIZE_DATASET.FORMAT),
                    record.get(AUTOPAGERIZE_DATASET.SOURCE_FILENAME),
                    record.get(AUTOPAGERIZE_DATASET.SOURCE_URI),
                    record.get(AUTOPAGERIZE_DATASET.SOURCE_SHA256),
                    record.get(AUTOPAGERIZE_DATASET.IMPORTER_VERSION),
                    record.get(AUTOPAGERIZE_DATASET.IMPORTED_AT),
                    record.get(AUTOPAGERIZE_DATASET.INPUT_ITEM_COUNT),
                    record.get(AUTOPAGERIZE_DATASET.ACCEPTED_RULE_COUNT),
                    record.get(AUTOPAGERIZE_DATASET.REJECTED_RULE_COUNT)));
  }

  /**
   * Inserts dataset metadata and returns the generated id. Callers must insert rules/rejections
   * separately (typically in the same transaction).
   */
  public long insert(AutoPagerizeDatasetCreate create) {
    return dsl.insertInto(AUTOPAGERIZE_DATASET)
        .set(AUTOPAGERIZE_DATASET.FORMAT, create.format())
        .set(AUTOPAGERIZE_DATASET.SOURCE_FILENAME, create.sourceFilename())
        .set(AUTOPAGERIZE_DATASET.SOURCE_URI, create.sourceUri())
        .set(AUTOPAGERIZE_DATASET.SOURCE_SHA256, create.sourceSha256())
        .set(AUTOPAGERIZE_DATASET.IMPORTER_VERSION, create.importerVersion())
        .set(AUTOPAGERIZE_DATASET.INPUT_ITEM_COUNT, create.inputItemCount())
        .set(AUTOPAGERIZE_DATASET.ACCEPTED_RULE_COUNT, create.acceptedRuleCount())
        .set(AUTOPAGERIZE_DATASET.REJECTED_RULE_COUNT, create.rejectedRuleCount())
        .set(AUTOPAGERIZE_DATASET.METADATA, JSONB.valueOf(create.metadataJson()))
        .returning(AUTOPAGERIZE_DATASET.ID)
        .fetchSingle(AUTOPAGERIZE_DATASET.ID);
  }

  private AutoPagerizeDataset mapDataset(Record record) {
    return new AutoPagerizeDataset(
        record.get(AUTOPAGERIZE_DATASET.ID),
        record.get(AUTOPAGERIZE_DATASET.FORMAT),
        record.get(AUTOPAGERIZE_DATASET.SOURCE_FILENAME),
        record.get(AUTOPAGERIZE_DATASET.SOURCE_URI),
        record.get(AUTOPAGERIZE_DATASET.SOURCE_SHA256),
        record.get(AUTOPAGERIZE_DATASET.IMPORTER_VERSION),
        record.get(AUTOPAGERIZE_DATASET.IMPORTED_AT),
        record.get(AUTOPAGERIZE_DATASET.INPUT_ITEM_COUNT),
        record.get(AUTOPAGERIZE_DATASET.ACCEPTED_RULE_COUNT),
        record.get(AUTOPAGERIZE_DATASET.REJECTED_RULE_COUNT),
        record.get(AUTOPAGERIZE_DATASET.METADATA).data());
  }
}
