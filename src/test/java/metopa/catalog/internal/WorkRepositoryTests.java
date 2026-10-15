package metopa.catalog.internal;

import metopa.catalog.PresentationMode;
import metopa.catalog.ReadingDirection;
import metopa.catalog.WorkType;
import metopa.catalog.Genre;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.UUID;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class WorkRepositoryTests {

    @Autowired
    private WorkRepository repository;

    @Test
    void shouldPersistWork() {
        Work work = new Work(
                java.util.UUID.randomUUID(),
                "Metopa Origins",
                "A graphic novel used to validate the catalog domain.",
                WorkType.GRAPHIC_NOVEL,
                ReadingDirection.LEFT_TO_RIGHT,
                PresentationMode.SINGLE_PAGE
        );

        Work saved = repository.saveAndFlush(work);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTitle())
                .isEqualTo("Metopa Origins");
        assertThat(saved.getType())
                .isEqualTo(WorkType.GRAPHIC_NOVEL);
        assertThat(saved.getReadingDirection())
                .isEqualTo(ReadingDirection.LEFT_TO_RIGHT);
        assertThat(saved.getPresentationMode())
                .isEqualTo(PresentationMode.SINGLE_PAGE);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldPersistWorkCoverReference() {
        UUID ownerId = UUID.randomUUID();

        Work work =
                new Work(
                        ownerId,
                        "Covered Work",
                        null,
                        WorkType.COMIC,
                        ReadingDirection.LEFT_TO_RIGHT,
                        PresentationMode.SINGLE_PAGE
                );

        work.setCover(
                "covers/" + work.getId() + "/cover-file",
                "image/webp"
        );

        repository.saveAndFlush(work);

        Work found =
                repository.findById(
                        work.getId()
                ).orElseThrow();

        assertThat(found.getCoverStorageKey())
                .isEqualTo(
                        "covers/"
                                + work.getId()
                                + "/cover-file"
                );

        assertThat(found.getCoverContentType())
                .isEqualTo("image/webp");
    }

    @Test
    void shouldPersistWorkGenres() {
        UUID ownerId = UUID.randomUUID();

        Work work =
                new Work(
                        ownerId,
                        "Genre Work",
                        "A work with multiple genres.",
                        WorkType.COMIC,
                        ReadingDirection.LEFT_TO_RIGHT,
                        PresentationMode.SINGLE_PAGE
                );

        work.setGenres(
                Set.of(
                        Genre.FANTASY,
                        Genre.ADVENTURE
                )
        );

        repository.saveAndFlush(work);

        Work found =
                repository.findById(
                        work.getId()
                ).orElseThrow();

        assertThat(found.getGenres())
                .containsExactlyInAnyOrder(
                        Genre.FANTASY,
                        Genre.ADVENTURE
                );
    }
}