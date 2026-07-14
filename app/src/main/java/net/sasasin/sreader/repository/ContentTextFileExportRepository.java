package net.sasasin.sreader.repository;

import static net.sasasin.sreader.jooq.Tables.CONTENT_FULL_TEXT;
import static net.sasasin.sreader.jooq.Tables.CONTENT_HEADER;
import static net.sasasin.sreader.jooq.Tables.CONTENT_TEXT_FILE_EXPORT;

import java.time.OffsetDateTime;
import java.util.List;
import net.sasasin.sreader.domain.ContentTextFileExportTarget;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class ContentTextFileExportRepository {

  private final DSLContext dsl;

  public ContentTextFileExportRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public List<ContentTextFileExportTarget> findUnexported(int limit) {
    return dsl.select(
            CONTENT_HEADER.ID,
            CONTENT_HEADER.CANONICAL_URL,
            CONTENT_HEADER.TITLE,
            CONTENT_FULL_TEXT.ID,
            CONTENT_FULL_TEXT.FULL_TEXT)
        .from(CONTENT_HEADER)
        .join(CONTENT_FULL_TEXT)
        .on(CONTENT_FULL_TEXT.CONTENT_HEADER_ID.eq(CONTENT_HEADER.ID))
        .leftJoin(CONTENT_TEXT_FILE_EXPORT)
        .on(CONTENT_TEXT_FILE_EXPORT.CONTENT_HEADER_ID.eq(CONTENT_HEADER.ID))
        .where(CONTENT_TEXT_FILE_EXPORT.CONTENT_HEADER_ID.isNull())
        .and(CONTENT_FULL_TEXT.FULL_TEXT.isNotNull())
        .and(DSL.trim(CONTENT_FULL_TEXT.FULL_TEXT).ne(""))
        .orderBy(CONTENT_HEADER.CREATED_AT.asc())
        .limit(limit)
        .fetch(
            record ->
                new ContentTextFileExportTarget(
                    record.get(CONTENT_HEADER.ID),
                    record.get(CONTENT_HEADER.CANONICAL_URL),
                    record.get(CONTENT_HEADER.TITLE),
                    record.get(CONTENT_FULL_TEXT.ID),
                    record.get(CONTENT_FULL_TEXT.FULL_TEXT)));
  }

  public boolean insertExported(
      String contentHeaderId,
      String contentFullTextId,
      String relativePath,
      long fileSizeBytes,
      String fileSha256) {
    OffsetDateTime now = OffsetDateTime.now();
    return dsl.insertInto(CONTENT_TEXT_FILE_EXPORT)
            .set(CONTENT_TEXT_FILE_EXPORT.CONTENT_HEADER_ID, contentHeaderId)
            .set(CONTENT_TEXT_FILE_EXPORT.CONTENT_FULL_TEXT_ID, contentFullTextId)
            .set(CONTENT_TEXT_FILE_EXPORT.RELATIVE_PATH, relativePath)
            .set(CONTENT_TEXT_FILE_EXPORT.FILE_SIZE_BYTES, fileSizeBytes)
            .set(CONTENT_TEXT_FILE_EXPORT.FILE_SHA256, fileSha256)
            .set(CONTENT_TEXT_FILE_EXPORT.EXPORTED_AT, now)
            .set(CONTENT_TEXT_FILE_EXPORT.CREATED_AT, now)
            .set(CONTENT_TEXT_FILE_EXPORT.UPDATED_AT, now)
            .onConflict(CONTENT_TEXT_FILE_EXPORT.CONTENT_HEADER_ID)
            .doNothing()
            .execute()
        == 1;
  }
}
