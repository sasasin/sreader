ALTER TABLE feed_url
	ADD COLUMN full_text_method varchar(64) NOT NULL DEFAULT 'http';

ALTER TABLE feed_url
	ADD CONSTRAINT feed_url_full_text_method_check
	CHECK (
		full_text_method IN (
			'http',
			'feed',
			'playwright',
			'playwright_readability',
			'playwright_infy_scroll',
			'playwright_infy_scroll_readability'
		)
	);

UPDATE feed_url
	SET full_text_method = 'http'
	WHERE full_text_method IS NULL;
