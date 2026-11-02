package metopa.publication.internal;

import metopa.publication.InstallmentType;
import metopa.publication.PublicationStatus;
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
class InstallmentRepositoryTests {

    @Autowired
    private InstallmentRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistInstallmentAsDraft() {
        UUID workId = UUID.randomUUID();

        insertTestWork(workId);

        Installment installment = new Installment(
                workId,
                InstallmentType.CHAPTER,
                1,
                null
        );

        Installment saved =
                repository.saveAndFlush(installment);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getWorkId()).isEqualTo(workId);
        assertThat(saved.getType())
                .isEqualTo(InstallmentType.CHAPTER);
        assertThat(saved.getNumber()).isEqualTo(1);
        assertThat(saved.getTitle()).isNull();
        assertThat(saved.getStatus())
                .isEqualTo(PublicationStatus.DRAFT);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
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
                "Repository Test Work",
                "A work created for persistence testing.",
                "COMIC",
                "LEFT_TO_RIGHT",
                "SINGLE_PAGE"
        );
    }
}
