CREATE TABLE content_text_file_export (
    content_header_id char(32) PRIMARY KEY,
    content_full_text_id char(32) NOT NULL,
    relative_path text NOT NULL,
    file_size_bytes bigint NOT NULL,
    file_sha256 char(64) NOT NULL,
    exported_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT content_text_file_export_header_fkey
        FOREIGN KEY (content_header_id)
        REFERENCES content_header(id)
        ON DELETE CASCADE,

    CONSTRAINT content_text_file_export_full_text_fkey
        FOREIGN KEY (content_full_text_id)
        REFERENCES content_full_text(id)
        ON DELETE CASCADE,

    CONSTRAINT content_text_file_export_full_text_key
        UNIQUE (content_full_text_id),

    CONSTRAINT content_text_file_export_relative_path_key
        UNIQUE (relative_path),

    CONSTRAINT content_text_file_export_relative_path_not_absolute_check
        CHECK (relative_path !~ '^/'),

    CONSTRAINT content_text_file_export_relative_path_no_directory_check
        CHECK (relative_path !~ '/'),

    CONSTRAINT content_text_file_export_relative_path_txt_check
        CHECK (relative_path ~ '^[0-9a-f]{32}\.txt$'),

    CONSTRAINT content_text_file_export_file_size_check
        CHECK (file_size_bytes >= 0)
);

CREATE INDEX content_text_file_export_exported_at_idx
    ON content_text_file_export(exported_at);
