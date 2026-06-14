-- Development seed data intentionally avoids real subscriptions and secrets.
-- Feed URLs can be supplied with SREADER_SEED_FEED_URLS or inserted manually.
INSERT INTO eft_rules(id, url_pattern, extract_rule)
VALUES('d41d8cd98f00b204e9800998ecf8427e', 'https://example.com/articles/', '//article')
ON CONFLICT (id) DO NOTHING;
