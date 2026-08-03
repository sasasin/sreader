-- Keep rejection errors machine-structured and remove redundant dataset-only indexes.

ALTER TABLE autopagerize_rule_rejection
    ADD CONSTRAINT autopagerize_rule_rejection_errors_shape_check
    CHECK (jsonb_typeof(errors) IN ('array', 'object'));

DROP INDEX autopagerize_rule_dataset_id_idx;
DROP INDEX autopagerize_rule_rejection_dataset_id_idx;
