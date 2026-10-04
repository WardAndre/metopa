CREATE TABLE installment_page (
    id UUID PRIMARY KEY,
    installment_id UUID NOT NULL,
    page_number INTEGER NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT ck_installment_page_number_positive
        CHECK (page_number > 0),

    CONSTRAINT uk_installment_page_number
        UNIQUE (installment_id, page_number),

    CONSTRAINT fk_installment_page_installment
        FOREIGN KEY (installment_id)
        REFERENCES installment(id)
        ON DELETE CASCADE
);