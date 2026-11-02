package metopa.library.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class LibraryEntryRepositoryTests {

    @Autowired
    private LibraryEntryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistLibraryEntry() {
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        insertTestWork(workId);

        LibraryEntry entry =
                new LibraryEntry(
                        userId,
                        workId
                );

        LibraryEntry saved =
                repository.saveAndFlush(entry);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId())
                .isEqualTo(userId);
        assertThat(saved.getWorkId())
                .isEqualTo(workId);
        assertThat(saved.getAddedAt())
                .isNotNull();
    }

    private void insertTestWork(UUID workId) {
        jdbcTemplate.update(
                """
                INSERT INTO work (
                    id,
                    owner_id,
                    title,
                    description,
                    work_type,
                    reading_direction,
                    presentation_mode,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """,
                workId,
                UUID.randomUUID(),
                "Library Repository Test Work",
                "A work created for library persistence testing.",
                "COMIC",
                "LEFT_TO_RIGHT",
                "SINGLE_PAGE"
        );
    }
}
