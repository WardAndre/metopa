package metopa.publication.internal;

import metopa.publication.InstallmentType;
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
class PageRepositoryTests {

    @Autowired
    private InstallmentRepository installmentRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistPageForInstallment() {
        UUID workId = UUID.randomUUID();

        insertTestWork(workId);

        Installment installment = new Installment(
                workId,
                InstallmentType.CHAPTER,
                1,
                "The Beginning"
        );

        Installment savedInstallment =
                installmentRepository.saveAndFlush(installment);

        Page page = new Page(
                savedInstallment.getId(),
                1,
                "works/example/chapter-1/page-1.jpg",
                "image/jpeg"
        );

        Page saved =
                pageRepository.saveAndFlush(page);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getInstallmentId())
                .isEqualTo(savedInstallment.getId());
        assertThat(saved.getNumber()).isEqualTo(1);
        assertThat(saved.getStorageKey())
                .isEqualTo(
                        "works/example/chapter-1/page-1.jpg"
                );
        assertThat(saved.getContentType())
                .isEqualTo("image/jpeg");
        assertThat(saved.getCreatedAt()).isNotNull();
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
