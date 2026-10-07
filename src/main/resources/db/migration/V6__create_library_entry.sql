CREATE TABLE library_entry (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    work_id UUID NOT NULL,
    added_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uk_library_entry_user_work
        UNIQUE (user_id, work_id)
);