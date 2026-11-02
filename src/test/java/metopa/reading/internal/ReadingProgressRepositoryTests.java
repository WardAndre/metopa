package metopa.reading.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ReadingProgressRepositoryTests {

    @Autowired
    private ReadingProgressRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistReadingProgress() {
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();

        insertTestWork(workId);

        ReadingProgress progress =
                new ReadingProgress(
                        userId,
                        workId,
                        installmentId,
                        pageId
                );

        ReadingProgress saved =
                repository.saveAndFlush(progress);

        ReadingProgress found =
                repository.findByUserIdAndWorkId(
                                userId,
                                workId
                        )
                        .orElseThrow();

        assertThat(found.getId())
                .isEqualTo(saved.getId());

        assertThat(found.getUserId())
                .isEqualTo(userId);

        assertThat(found.getWorkId())
                .isEqualTo(workId);

        assertThat(found.getInstallmentId())
                .isEqualTo(installmentId);

        assertThat(found.getPageId())
                .isEqualTo(pageId);

        assertThat(found.getUpdatedAt())
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
                "Reading Repository Test Work",
                "A work created for reading persistence testing.",
                "COMIC",
                "LEFT_TO_RIGHT",
                "SINGLE_PAGE"
        );
    }
}
