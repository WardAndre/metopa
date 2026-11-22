package metopa.catalog.internal;

import jakarta.persistence.EntityManager;
import metopa.catalog.PresentationMode;
import metopa.catalog.ReadingDirection;
import metopa.catalog.WorkType;
import metopa.catalog.WorkVisibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class WorkVisibilityRepositoryTests {

    @Autowired
    private WorkRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistDefaultPublicVisibility() {
        Work work = createWork();

        assertThat(work.getVisibility())
                .isEqualTo(WorkVisibility.PUBLIC);

        work = repository.saveAndFlush(work);

        entityManager.clear();

        Work persisted = repository.findById(work.getId())
                .orElseThrow();

        assertThat(persisted.getVisibility())
                .isEqualTo(WorkVisibility.PUBLIC);

        String storedVisibility = jdbcTemplate.queryForObject(
                """
                SELECT visibility
                FROM work
                WHERE id = ?
                """,
                String.class,
                work.getId()
        );

        assertThat(storedVisibility)
                .isEqualTo("PUBLIC");
    }

    @Test
    void shouldLoadRestrictedVisibilityFromDatabase() {
        Work work = repository.saveAndFlush(createWork());

        int updatedRows = jdbcTemplate.update(
                """
                UPDATE work
                SET visibility = 'RESTRICTED'
                WHERE id = ?
                """,
                work.getId()
        );

        assertThat(updatedRows).isEqualTo(1);

        entityManager.clear();

        Work persisted = repository.findById(work.getId())
                .orElseThrow();

        assertThat(persisted.getVisibility())
                .isEqualTo(WorkVisibility.RESTRICTED);
    }

    @Test
    void shouldRejectUnknownVisibilityAtDatabaseLevel() {
        Work work = repository.saveAndFlush(createWork());

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        """
                        UPDATE work
                        SET visibility = 'UNKNOWN'
                        WHERE id = ?
                        """,
                        work.getId()
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldPersistRestrictedVisibilityTransition() {
        Work work = repository.saveAndFlush(createWork());

        assertThat(entityManager.contains(work))
                .isTrue();

        work.restrictVisibility();

        assertThat(work.getVisibility())
                .isEqualTo(WorkVisibility.RESTRICTED);

        repository.flush();
        entityManager.clear();

        Work persisted = repository.findById(work.getId())
                .orElseThrow();

        assertThat(persisted.getVisibility())
                .isEqualTo(WorkVisibility.RESTRICTED);

        String storedVisibility = jdbcTemplate.queryForObject(
                """
                SELECT visibility
                FROM work
                WHERE id = ?
                """,
                String.class,
                work.getId()
        );

        assertThat(storedVisibility)
                .isEqualTo("RESTRICTED");
    }

    @Test
    void shouldPersistRestoredVisibilityTransition() {
        Work work = repository.saveAndFlush(createWork());

        assertThat(entityManager.contains(work))
                .isTrue();

        work.restrictVisibility();

        repository.flush();

        String restrictedVisibility = jdbcTemplate.queryForObject(
                """
                SELECT visibility
                FROM work
                WHERE id = ?
                """,
                String.class,
                work.getId()
        );

        assertThat(restrictedVisibility)
                .isEqualTo("RESTRICTED");

        work.restoreVisibility();

        assertThat(work.getVisibility())
                .isEqualTo(WorkVisibility.PUBLIC);

        repository.flush();
        entityManager.clear();

        Work persisted = repository.findById(work.getId())
                .orElseThrow();

        assertThat(persisted.getVisibility())
                .isEqualTo(WorkVisibility.PUBLIC);

        String storedVisibility = jdbcTemplate.queryForObject(
                """
                SELECT visibility
                FROM work
                WHERE id = ?
                """,
                String.class,
                work.getId()
        );

        assertThat(storedVisibility)
                .isEqualTo("PUBLIC");
    }

    private Work createWork() {
        return new Work(
                UUID.randomUUID(),
                "Metopa Visibility Test",
                "Work used to validate moderation visibility.",
                WorkType.GRAPHIC_NOVEL,
                ReadingDirection.LEFT_TO_RIGHT,
                PresentationMode.SINGLE_PAGE
        );
    }
}
