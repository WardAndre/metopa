package metopa.catalog.internal;

import metopa.catalog.PresentationMode;
import metopa.catalog.ReadingDirection;
import metopa.catalog.WorkType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

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
}