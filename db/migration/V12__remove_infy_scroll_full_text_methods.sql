-- Phase 7: retire Infy Scroll full-text methods.
-- Convert legacy feed method values, then tighten the check constraint to the final catalog.

UPDATE feed_url
SET full_text_method = 'playwright_autopagerize'
WHERE full_text_method = 'playwright_infy_scroll';

UPDATE feed_url
SET full_text_method = 'playwright_autopagerize_readability'
WHERE full_text_method = 'playwright_infy_scroll_readability';

ALTER TABLE feed_url
    DROP CONSTRAINT feed_url_full_text_method_check;

ALTER TABLE feed_url
    ADD CONSTRAINT feed_url_full_text_method_check
    CHECK (
        full_text_method IN (
            'feed',
            'http',
            'http_readability',
            'http_autopagerize',
            'http_autopagerize_readability',
            'playwright',
            'playwright_readability',
            'playwright_autopagerize',
            'playwright_autopagerize_readability'
        )
    );
