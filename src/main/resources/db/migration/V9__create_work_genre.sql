CREATE TABLE work_genre (
    work_id UUID NOT NULL,
    genre VARCHAR(50) NOT NULL,

    CONSTRAINT pk_work_genre
        PRIMARY KEY (work_id, genre),

    CONSTRAINT fk_work_genre_work
        FOREIGN KEY (work_id)
        REFERENCES work(id)
        ON DELETE CASCADE
);