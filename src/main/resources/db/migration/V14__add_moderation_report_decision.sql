ALTER TABLE moderation_report
    ADD COLUMN decision VARCHAR(32);

ALTER TABLE moderation_report
    ADD CONSTRAINT chk_moderation_report_decision
    CHECK (
        (
            status IN ('OPEN', 'UNDER_REVIEW')
            AND decision IS NULL
        )
        OR
        (
            status = 'RESOLVED'
            AND decision = 'NO_VIOLATION'
        )
    );