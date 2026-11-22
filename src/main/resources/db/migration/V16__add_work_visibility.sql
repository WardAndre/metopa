-- Separate administrative visibility from editorial publication state.

ALTER TABLE work
    ADD COLUMN visibility VARCHAR(20)
    NOT NULL DEFAULT 'PUBLIC';

ALTER TABLE work
    ADD CONSTRAINT chk_work_visibility
    CHECK (
        visibility IN (
            'PUBLIC',
            'RESTRICTED'
        )
    );
