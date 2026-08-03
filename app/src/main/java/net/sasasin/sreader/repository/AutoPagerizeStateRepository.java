package net.sasasin.sreader.repository;

import static net.sasasin.sreader.jooq.Tables.AUTOPAGERIZE_DATASET;
import static net.sasasin.sreader.jooq.Tables.AUTOPAGERIZE_STATE;

import java.time.OffsetDateTime;
import java.util.Optional;
import net.sasasin.sreader.domain.AutoPagerizeActiveState;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/**
 * Singleton active AutoPagerize dataset pointer. Prefer {@link #lockActiveState()} inside a service
 * transaction before {@link #activateDataset(long)} when atomic switch is required.
 */
@Repository
public class AutoPagerizeStateRepository {

  private static final short SINGLETON_ID = 1;

  private final DSLContext dsl;

  public AutoPagerizeStateRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public Optional<Long> findActiveDatasetId() {
    return Optional.ofNullable(
        dsl.select(AUTOPAGERIZE_STATE.ACTIVE_DATASET_ID)
            .from(AUTOPAGERIZE_STATE)
            .where(AUTOPAGERIZE_STATE.ID.eq(SINGLETON_ID))
            .fetchOne(AUTOPAGERIZE_STATE.ACTIVE_DATASET_ID));
  }

  public AutoPagerizeActiveState findActiveState() {
    return dsl.select(AUTOPAGERIZE_STATE.ACTIVE_DATASET_ID, AUTOPAGERIZE_STATE.ACTIVATED_AT)
        .from(AUTOPAGERIZE_STATE)
        .where(AUTOPAGERIZE_STATE.ID.eq(SINGLETON_ID))
        .fetchSingle(this::mapState);
  }

  /**
   * Locks the singleton state row with {@code FOR UPDATE}. Must be called inside an open
   * transaction; otherwise the lock is released immediately.
   */
  public AutoPagerizeActiveState lockActiveState() {
    return dsl.select(AUTOPAGERIZE_STATE.ACTIVE_DATASET_ID, AUTOPAGERIZE_STATE.ACTIVATED_AT)
        .from(AUTOPAGERIZE_STATE)
        .where(AUTOPAGERIZE_STATE.ID.eq(SINGLETON_ID))
        .forUpdate()
        .fetchSingle(this::mapState);
  }

  /**
   * Points the singleton at {@code datasetId}. Rejects unknown datasets with {@link
   * IllegalArgumentException}. Does not open its own transaction.
   */
  public void activateDataset(long datasetId) {
    boolean exists =
        dsl.fetchExists(
            dsl.selectOne()
                .from(AUTOPAGERIZE_DATASET)
                .where(AUTOPAGERIZE_DATASET.ID.eq(datasetId)));
    if (!exists) {
      throw new IllegalArgumentException("AutoPagerize dataset does not exist: " + datasetId);
    }
    OffsetDateTime now = OffsetDateTime.now();
    dsl.update(AUTOPAGERIZE_STATE)
        .set(AUTOPAGERIZE_STATE.ACTIVE_DATASET_ID, datasetId)
        .set(AUTOPAGERIZE_STATE.ACTIVATED_AT, now)
        .where(AUTOPAGERIZE_STATE.ID.eq(SINGLETON_ID))
        .execute();
  }

  /** Clears the active pointer (for tests / maintenance). Does not open its own transaction. */
  public void clearActiveDataset() {
    dsl.update(AUTOPAGERIZE_STATE)
        .set(AUTOPAGERIZE_STATE.ACTIVE_DATASET_ID, (Long) null)
        .set(AUTOPAGERIZE_STATE.ACTIVATED_AT, (OffsetDateTime) null)
        .where(AUTOPAGERIZE_STATE.ID.eq(SINGLETON_ID))
        .execute();
  }

  private AutoPagerizeActiveState mapState(Record record) {
    return new AutoPagerizeActiveState(
        record.get(AUTOPAGERIZE_STATE.ACTIVE_DATASET_ID),
        record.get(AUTOPAGERIZE_STATE.ACTIVATED_AT));
  }
}
