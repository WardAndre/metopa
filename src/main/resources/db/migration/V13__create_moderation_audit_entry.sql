-- Persist an audit trail for administrative report actions.

CREATE TABLE moderation_audit_entry (
    id UUID PRIMARY KEY,

    report_id UUID NOT NULL,
    admin_user_id UUID NOT NULL,

    action VARCHAR(32) NOT NULL,
    note VARCHAR(2000) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_moderation_audit_report
        FOREIGN KEY (report_id)
        REFERENCES moderation_report(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_moderation_audit_action
        CHECK (action IN ('REVIEW_STARTED')),

    CONSTRAINT chk_moderation_audit_note
        CHECK (length(btrim(note)) > 0)
);

CREATE INDEX idx_moderation_audit_report_created_at
    ON moderation_audit_entry (report_id, created_at);