-- Prevent deleting works that still have installments.
ALTER TABLE installment
    ADD CONSTRAINT fk_installment_work
    FOREIGN KEY (work_id)
    REFERENCES work(id)
    ON DELETE RESTRICT;

-- Remove library entries when their work is deleted.
ALTER TABLE library_entry
    ADD CONSTRAINT fk_library_entry_work
    FOREIGN KEY (work_id)
    REFERENCES work(id)
    ON DELETE CASCADE;

-- Remove reading progress when its work is deleted.
ALTER TABLE reading_progress
    ADD CONSTRAINT fk_reading_progress_work
    FOREIGN KEY (work_id)
    REFERENCES work(id)
    ON DELETE CASCADE;
