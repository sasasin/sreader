-- AutoPagerize immutable dataset catalog, active pointer, and full-text extraction metadata.

CREATE TABLE autopagerize_dataset (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    format varchar(64) NOT NULL,
    source_filename text,
    source_uri text,
    source_sha256 char(64) NOT NULL,
    importer_version integer NOT NULL,
    imported_at timestamptz NOT NULL DEFAULT now(),
    input_item_count integer NOT NULL,
    accepted_rule_count integer NOT NULL,
    rejected_rule_count integer NOT NULL,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,

    CONSTRAINT autopagerize_dataset_format_check CHECK (
        format IN ('wedata-autopagerize-items-all')
    ),
    CONSTRAINT autopagerize_dataset_source_sha256_check CHECK (
        source_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT autopagerize_dataset_counts_check CHECK (
        input_item_count >= 0
        AND accepted_rule_count >= 0
        AND rejected_rule_count >= 0
        AND accepted_rule_count + rejected_rule_count = input_item_count
    ),
    CONSTRAINT autopagerize_dataset_identity_key
        UNIQUE (format, source_sha256, importer_version)
);

CREATE TABLE autopagerize_rule (
    dataset_id bigint NOT NULL
        REFERENCES autopagerize_dataset(id) ON DELETE CASCADE,
    ordinal integer NOT NULL,
    match_order integer NOT NULL,

    external_id text,
    resource_url text,
    name text,
    created_by text,
    source_created_at timestamptz,
    source_updated_at timestamptz,

    url_pattern text NOT NULL,
    next_link_xpath text NOT NULL,
    page_element_xpath text NOT NULL,
    insert_before_xpath text,
    example_url text,

    raw_item jsonb NOT NULL,

    PRIMARY KEY (dataset_id, ordinal),
    CONSTRAINT autopagerize_rule_match_order_key
        UNIQUE (dataset_id, match_order),
    CONSTRAINT autopagerize_rule_ordinal_check CHECK (ordinal >= 0),
    CONSTRAINT autopagerize_rule_match_order_check CHECK (match_order >= 0)
);

-- UNIQUE (dataset_id, match_order) already supports ordered load by dataset.
CREATE INDEX autopagerize_rule_dataset_id_idx ON autopagerize_rule (dataset_id);

CREATE TABLE autopagerize_rule_rejection (
    dataset_id bigint NOT NULL
        REFERENCES autopagerize_dataset(id) ON DELETE CASCADE,
    ordinal integer NOT NULL,
    name text,
    raw_item jsonb NOT NULL,
    errors jsonb NOT NULL,
    PRIMARY KEY (dataset_id, ordinal),
    CONSTRAINT autopagerize_rule_rejection_ordinal_check CHECK (ordinal >= 0)
);

CREATE INDEX autopagerize_rule_rejection_dataset_id_idx
    ON autopagerize_rule_rejection (dataset_id);

CREATE TABLE autopagerize_state (
    id smallint PRIMARY KEY,
    active_dataset_id bigint
        REFERENCES autopagerize_dataset(id) ON DELETE RESTRICT,
    activated_at timestamptz,
    CONSTRAINT autopagerize_state_singleton_check CHECK (id = 1)
);

INSERT INTO autopagerize_state (id, active_dataset_id, activated_at)
VALUES (1, NULL, NULL);

ALTER TABLE content_full_text
    ADD COLUMN autopagerize_dataset_id bigint,
    ADD COLUMN autopagerize_rule_ordinal integer,
    ADD COLUMN pagination_page_count integer,
    ADD COLUMN pagination_stop_reason varchar(64),
    ADD COLUMN pagination_complete boolean;

ALTER TABLE content_full_text
    ADD CONSTRAINT content_full_text_autopagerize_dataset_fkey
        FOREIGN KEY (autopagerize_dataset_id)
        REFERENCES autopagerize_dataset(id) ON DELETE RESTRICT,
    ADD CONSTRAINT content_full_text_autopagerize_rule_fkey
        FOREIGN KEY (autopagerize_dataset_id, autopagerize_rule_ordinal)
        REFERENCES autopagerize_rule(dataset_id, ordinal),
    ADD CONSTRAINT content_full_text_pagination_page_count_check
        CHECK (pagination_page_count IS NULL OR pagination_page_count >= 1),
    ADD CONSTRAINT content_full_text_autopagerize_rule_pair_check
        CHECK (
            autopagerize_rule_ordinal IS NULL
            OR autopagerize_dataset_id IS NOT NULL
        );

-- Transitional full-text methods: AutoPagerize methods are added; Infy methods remain until phase 7.
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
            'playwright_autopagerize_readability',
            'playwright_infy_scroll',
            'playwright_infy_scroll_readability'
        )
    );
