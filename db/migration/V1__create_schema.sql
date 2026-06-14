CREATE TABLE feed_url(
       id char(32) primary key,
       url text not null,
       created_at timestamptz not null default now(),
       updated_at timestamptz not null default now(),
       CONSTRAINT feed_url_url_key UNIQUE (url)
);

CREATE TABLE content_header(
       id char(32) primary key,
       feed_url_id char(32) not null,
       url text not null,
       title varchar(1024),
       published_at timestamptz,
       created_at timestamptz not null default now(),
       updated_at timestamptz not null default now(),
       CONSTRAINT content_header_feed_url_fkey FOREIGN KEY (feed_url_id) REFERENCES feed_url(id) ON DELETE CASCADE,
       CONSTRAINT content_header_url_key UNIQUE (url)
);

CREATE INDEX content_header_feed_url_id_idx ON content_header(feed_url_id);

CREATE TABLE content_full_text(
       id char(32) primary key,
       content_header_id char(32) not null,
       full_text text,
       extracted_at timestamptz not null default now(),
       created_at timestamptz not null default now(),
       updated_at timestamptz not null default now(),
       CONSTRAINT content_full_text_content_header_fkey FOREIGN KEY (content_header_id) REFERENCES content_header(id) ON DELETE CASCADE,
       CONSTRAINT content_full_text_content_header_id_key UNIQUE (content_header_id)
);

CREATE TABLE eft_rules(
       id char(32) primary key,
       url_pattern text not null,
       extract_rule text not null,
       created_at timestamptz not null default now(),
       updated_at timestamptz not null default now()
);

CREATE INDEX eft_rules_url_pattern_idx ON eft_rules(url_pattern);
