package metopa.publication.internal;

import metopa.publication.InstallmentType;
import metopa.publication.PublicationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
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

    @Test
    void shouldPageDistinctPublishedWorkIds() {
        Long publishedWorksBeforeTest = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT work_id)
                FROM installment
                WHERE status = 'PUBLISHED'
                """,
                Long.class
        );

        UUID firstWorkId = UUID.randomUUID();
        UUID secondWorkId = UUID.randomUUID();
        UUID draftOnlyWorkId = UUID.randomUUID();

        insertTestWork(firstWorkId);
        insertTestWork(secondWorkId);
        insertTestWork(draftOnlyWorkId);

        insertPublishedInstallment(firstWorkId, 1);
        insertPublishedInstallment(firstWorkId, 2);
        insertPublishedInstallment(secondWorkId, 1);

        repository.saveAndFlush(
                new Installment(
                        draftOnlyWorkId,
                        InstallmentType.CHAPTER,
                        1,
                        "Draft Chapter"
                )
        );

        Page<UUID> firstPage =
                repository.findPublishedWorkIds(
                        PageRequest.of(0, 1)
                );

        Page<UUID> secondPage =
                repository.findPublishedWorkIds(
                        PageRequest.of(1, 1)
                );

        assertThat(firstPage.getContent()).hasSize(1);
        assertThat(secondPage.getContent()).hasSize(1);

        assertThat(
                List.of(
                        firstPage.getContent().getFirst(),
                        secondPage.getContent().getFirst()
                )
        ).containsExactlyInAnyOrder(
                firstWorkId,
                secondWorkId
        );

        long expectedTotalElements =
                publishedWorksBeforeTest + 2;

        int expectedTotalPages =
                Math.toIntExact(expectedTotalElements);

        assertThat(firstPage.getTotalElements())
                .isEqualTo(expectedTotalElements);

        assertThat(firstPage.getTotalPages())
                .isEqualTo(expectedTotalPages);

        assertThat(secondPage.getTotalElements())
                .isEqualTo(expectedTotalElements);

        assertThat(secondPage.getTotalPages())
                .isEqualTo(expectedTotalPages);
    }

    @Test
    void shouldFilterPublishedWorkIdsByGenreBeforePagination() {
        String genre = "SCI_FI";

        Long publishedWorksBeforeTest =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(DISTINCT i.work_id)
                        FROM installment i
                        WHERE i.status = 'PUBLISHED'
                          AND EXISTS (
                              SELECT 1
                              FROM work_genre wg
                              WHERE wg.work_id = i.work_id
                                AND wg.genre = ?
                          )
                        """,
                        Long.class,
                        genre
                );

        UUID scienceFictionWorkId = UUID.randomUUID();
        UUID dramaWorkId = UUID.randomUUID();
        UUID draftOnlyWorkId = UUID.randomUUID();

        insertTestWork(scienceFictionWorkId);
        insertTestWork(dramaWorkId);
        insertTestWork(draftOnlyWorkId);

        jdbcTemplate.update(
                "INSERT INTO work_genre (work_id, genre) VALUES (?, ?)",
                scienceFictionWorkId,
                genre
        );

        jdbcTemplate.update(
                "INSERT INTO work_genre (work_id, genre) VALUES (?, ?)",
                dramaWorkId,
                "DRAMA"
        );

        jdbcTemplate.update(
                "INSERT INTO work_genre (work_id, genre) VALUES (?, ?)",
                draftOnlyWorkId,
                genre
        );

        insertPublishedInstallment(scienceFictionWorkId, 1);
        insertPublishedInstallment(scienceFictionWorkId, 2);
        insertPublishedInstallment(dramaWorkId, 1);

        repository.saveAndFlush(
                new Installment(
                        draftOnlyWorkId,
                        InstallmentType.CHAPTER,
                        1,
                        "Draft Chapter"
                )
        );

        Page<UUID> firstPage =
                repository.findPublishedWorkIdsByGenre(
                        genre,
                        PageRequest.of(0, 1)
                );

        assertThat(firstPage.getContent()).hasSize(1);

        assertThat(firstPage.getTotalElements())
                .isEqualTo(publishedWorksBeforeTest + 1);

        assertThat(firstPage.getContent())
                .doesNotContain(
                        dramaWorkId,
                        draftOnlyWorkId
                );

        UUID returnedWorkId =
                firstPage.getContent().getFirst();

        Integer matchingGenres = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM work_genre
                WHERE work_id = ?
                  AND genre = ?
                """,
                Integer.class,
                returnedWorkId,
                genre
        );

        assertThat(matchingGenres).isEqualTo(1);
    }

    private void insertPublishedInstallment(
            UUID workId,
            int number
    ) {
        Installment installment = new Installment(
                workId,
                InstallmentType.CHAPTER,
                number,
                "Chapter " + number
        );

        installment.publish();

        repository.saveAndFlush(installment);
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