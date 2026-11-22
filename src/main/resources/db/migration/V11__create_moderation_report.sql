-- Store reports submitted against Metopa works.
-- Work references are intentionally not enforced by a foreign key
-- so reports can remain available after a work is deleted.

CREATE TABLE moderation_report (
    id UUID PRIMARY KEY,

    work_id UUID NOT NULL,
    reporter_user_id UUID NOT NULL,

    reason VARCHAR(32) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_moderation_report_reason
        CHECK (reason IN (
            'COPYRIGHT',
            'AGE_RATING',
            'PROHIBITED_CONTENT',
            'OTHER'
        )),

    CONSTRAINT chk_moderation_report_status
        CHECK (status IN (
            'OPEN',
            'UNDER_REVIEW',
            'RESOLVED'
        )),

    CONSTRAINT chk_moderation_report_description
        CHECK (length(btrim(description)) > 0)
);

-- Support administrative review queues.
CREATE INDEX idx_moderation_report_status_created_at
    ON moderation_report (status, created_at);

-- Support report lookup by work.
CREATE INDEX idx_moderation_report_work_id
    ON moderation_report (work_id);