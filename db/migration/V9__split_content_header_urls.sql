ALTER TABLE content_header RENAME COLUMN url TO canonical_url;
ALTER TABLE content_header ADD COLUMN source_url text;
ALTER TABLE content_header ADD COLUMN fetch_url text;

UPDATE content_header
SET source_url = canonical_url,
    fetch_url = canonical_url;

ALTER TABLE content_header ALTER COLUMN source_url SET NOT NULL;
ALTER TABLE content_header ALTER COLUMN fetch_url SET NOT NULL;
ALTER TABLE content_header RENAME CONSTRAINT content_header_url_key TO content_header_canonical_url_key;
