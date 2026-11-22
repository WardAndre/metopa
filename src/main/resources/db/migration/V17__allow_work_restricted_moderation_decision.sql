-- Allow work restriction as a moderation resolution decision.

ALTER TABLE moderation_report
    DROP CONSTRAINT chk_moderation_report_decision;

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
            AND decision IS NOT NULL
            AND decision IN (
                'NO_VIOLATION',
                'WORK_RESTRICTED'
            )
        )
    );