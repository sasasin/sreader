ALTER TABLE feed_url
	ADD COLUMN status text NOT NULL DEFAULT 'active',
	ADD COLUMN unsubscribe_reason text NULL,
	ADD COLUMN unsubscribed_at timestamptz NULL,
	ADD COLUMN note text NULL;

ALTER TABLE feed_url
	ADD CONSTRAINT feed_url_status_check
	CHECK (status IN ('active', 'unsubscribed'));

ALTER TABLE feed_url
	ADD CONSTRAINT feed_url_unsubscribe_reason_check
	CHECK (
		unsubscribe_reason IS NULL
		OR unsubscribe_reason IN ('not_interested', 'site_closed', 'feed_dead', 'moved', 'other')
	);

UPDATE feed_url
	SET status = 'active'
	WHERE status IS NULL;
