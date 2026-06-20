ALTER TABLE content_header
	ADD COLUMN feed_text text;

ALTER TABLE content_full_text
	ADD COLUMN extraction_method varchar(64),
	ADD COLUMN extraction_status varchar(32) NOT NULL DEFAULT 'success',
	ADD COLUMN error_message text,
	ADD COLUMN source_kind varchar(32),
	ADD COLUMN extracted_url text;
