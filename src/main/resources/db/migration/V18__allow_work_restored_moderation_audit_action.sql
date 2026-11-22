-- Allow work restoration events in the moderation audit trail.

ALTER TABLE moderation_audit_entry
    DROP CONSTRAINT chk_moderation_audit_action;

ALTER TABLE moderation_audit_entry
    ADD CONSTRAINT chk_moderation_audit_action
    CHECK (
        action IN (
            'REVIEW_STARTED',
            'REPORT_RESOLVED',
            'WORK_RESTORED'
        )
    );