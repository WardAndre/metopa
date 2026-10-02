CREATE TABLE work (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    work_type VARCHAR(30) NOT NULL,
    reading_direction VARCHAR(30) NOT NULL,
    presentation_mode VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);