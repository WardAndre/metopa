CREATE TABLE installment (
    id UUID PRIMARY KEY,
    work_id UUID NOT NULL,
    installment_type VARCHAR(30) NOT NULL,
    number INTEGER NOT NULL,
    title VARCHAR(200),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT ck_installment_number_positive
        CHECK (number > 0),

    CONSTRAINT uk_installment_work_type_number
        UNIQUE (work_id, installment_type, number)
);