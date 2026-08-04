package net.sasasin.sreader.repository;

import static net.sasasin.sreader.jooq.Tables.CONTENT_FULL_TEXT;

import java.time.OffsetDateTime;
import net.sasasin.sreader.domain.ContentFullText;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class ContentFullTextRepository {

  private final DSLContext dsl;

  public ContentFullTextRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public boolean insertIfAbsent(ContentFullText fullText) {
    OffsetDateTime now = OffsetDateTime.now();
    return dsl.insertInto(CONTENT_FULL_TEXT)
            .set(CONTENT_FULL_TEXT.ID, fullText.id())
            .set(CONTENT_FULL_TEXT.CONTENT_HEADER_ID, fullText.contentHeaderId())
            .set(CONTENT_FULL_TEXT.FULL_TEXT, fullText.fullText())
            .set(CONTENT_FULL_TEXT.EXTRACTION_METHOD, fullText.extractionMethod())
            .set(CONTENT_FULL_TEXT.EXTRACTION_STATUS, fullText.extractionStatus())
            .set(CONTENT_FULL_TEXT.ERROR_MESSAGE, fullText.errorMessage())
            .set(CONTENT_FULL_TEXT.SOURCE_KIND, fullText.sourceKind())
            .set(CONTENT_FULL_TEXT.EXTRACTED_URL, fullText.extractedUrl())
            .set(CONTENT_FULL_TEXT.EXTRACTED_AT, now)
            .set(CONTENT_FULL_TEXT.CREATED_AT, now)
            .set(CONTENT_FULL_TEXT.UPDATED_AT, now)
            .set(CONTENT_FULL_TEXT.AUTOPAGERIZE_DATASET_ID, fullText.autopagerizeDatasetId())
            .set(CONTENT_FULL_TEXT.AUTOPAGERIZE_RULE_ORDINAL, fullText.autopagerizeRuleOrdinal())
            .set(CONTENT_FULL_TEXT.PAGINATION_PAGE_COUNT, fullText.paginationPageCount())
            .set(CONTENT_FULL_TEXT.PAGINATION_STOP_REASON, fullText.paginationStopReason())
            .set(CONTENT_FULL_TEXT.PAGINATION_COMPLETE, fullText.paginationComplete())
            .onConflict(CONTENT_FULL_TEXT.ID)
            .doNothing()
            .execute()
        == 1;
  }
}
