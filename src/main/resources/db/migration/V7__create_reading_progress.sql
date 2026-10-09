CREATE TABLE reading_progress (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    work_id UUID NOT NULL,
    installment_id UUID NOT NULL,
    page_id UUID NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uk_reading_progress_user_work
        UNIQUE (user_id, work_id)
);