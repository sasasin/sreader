ALTER TABLE feed_url
    DROP CONSTRAINT feed_url_full_text_method_check;

ALTER TABLE feed_url
    ADD CONSTRAINT feed_url_full_text_method_check
    CHECK (
        full_text_method IN (
            'http',
            'http_readability',
            'feed',
            'playwright',
            'playwright_readability',
            'playwright_infy_scroll',
            'playwright_infy_scroll_readability'
        )
    );
