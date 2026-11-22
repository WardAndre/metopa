-- Require an explicit decision for resolved reports.

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
            AND decision = 'NO_VIOLATION'
        )
    );

-- Allow the resolution event in the moderation audit trail.

ALTER TABLE moderation_audit_entry
    DROP CONSTRAINT chk_moderation_audit_action;

ALTER TABLE moderation_audit_entry
    ADD CONSTRAINT chk_moderation_audit_action
    CHECK (
        action IN (
            'REVIEW_STARTED',
            'REPORT_RESOLVED'
        )
    );
