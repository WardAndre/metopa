package metopa.moderation.internal;

import jakarta.persistence.EntityManager;
import metopa.moderation.ModerationDecision;
import metopa.moderation.ReportReason;
import metopa.moderation.ReportStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ModerationReportRepositoryTests {

    @Autowired
    private ModerationReportRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Sort REPORT_ORDER = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.asc("id")
    );

    @Test
    void shouldPersistAndReloadReport() {
        UUID workId = UUID.randomUUID();
        UUID reporterUserId = UUID.randomUUID();

        ModerationReport report = new ModerationReport(
                workId,
                reporterUserId,
                ReportReason.COPYRIGHT,
                "Unauthorized reproduction of copyrighted content."
        );

        repository.saveAndFlush(report);

        entityManager.clear();

        ModerationReport persisted = repository
                .findById(report.getId())
                .orElseThrow();

        assertThat(persisted.getId())
                .isEqualTo(report.getId());

        assertThat(persisted.getWorkId())
                .isEqualTo(workId);

        assertThat(persisted.getReporterUserId())
                .isEqualTo(reporterUserId);

        assertThat(persisted.getReason())
                .isEqualTo(ReportReason.COPYRIGHT);

        assertThat(persisted.getDescription())
                .isEqualTo(
                        "Unauthorized reproduction of copyrighted content."
                );

        assertThat(persisted.getStatus())
                .isEqualTo(ReportStatus.OPEN);

        assertThat(persisted.getCreatedAt())
                .isNotNull();

        assertThat(persisted.getUpdatedAt())
                .isEqualTo(persisted.getCreatedAt());
    }

    @Test
    void shouldAllowMultipleReportsForSameWork() {
        UUID workId = UUID.randomUUID();

        ModerationReport firstReport = new ModerationReport(
                workId,
                UUID.randomUUID(),
                ReportReason.COPYRIGHT,
                "Possible copyright infringement."
        );

        ModerationReport secondReport = new ModerationReport(
                workId,
                UUID.randomUUID(),
                ReportReason.AGE_RATING,
                "Incorrect age classification."
        );

        repository.saveAllAndFlush(
                List.of(firstReport, secondReport)
        );

        List<ModerationReport> persisted = repository.findAllById(
                List.of(
                        firstReport.getId(),
                        secondReport.getId()
                )
        );

        assertThat(persisted).hasSize(2);

        assertThat(persisted)
                .extracting(ModerationReport::getWorkId)
                .containsOnly(workId);

        assertThat(persisted)
                .extracting(ModerationReport::getReason)
                .containsExactlyInAnyOrder(
                        ReportReason.COPYRIGHT,
                        ReportReason.AGE_RATING
                );
    }

    @Test
    void shouldRejectInvalidStatusAtDatabaseLevel() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO moderation_report (
                    id,
                    work_id,
                    reporter_user_id,
                    reason,
                    description,
                    status,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "COPYRIGHT",
                "Invalid status test.",
                "INVALID"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldReturnPaginatedReportsInDeterministicOrder() {
        UUID workId = UUID.randomUUID();

        ModerationReport first = new ModerationReport(
                workId,
                UUID.randomUUID(),
                ReportReason.COPYRIGHT,
                "First report."
        );

        ModerationReport second = new ModerationReport(
                workId,
                UUID.randomUUID(),
                ReportReason.AGE_RATING,
                "Second report."
        );

        ModerationReport third = new ModerationReport(
                workId,
                UUID.randomUUID(),
                ReportReason.OTHER,
                "Third report."
        );

        repository.saveAllAndFlush(
                List.of(first, second, third)
        );

        Page<ModerationReport> page = repository.findAll(
                PageRequest.of(0, 2, REPORT_ORDER)
        );

        List<UUID> expectedIds = jdbcTemplate.queryForList(
                """
                SELECT id
                FROM moderation_report
                ORDER BY created_at DESC, id ASC
                LIMIT 2
                """,
                UUID.class
        );

        Long totalReports = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM moderation_report",
                Long.class
        );

        assertThat(page.getNumber()).isZero();
        assertThat(page.getSize()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);

        assertThat(page.getTotalElements())
                .isEqualTo(totalReports);

        assertThat(page.getContent())
                .extracting(ModerationReport::getId)
                .containsExactlyElementsOf(expectedIds);
    }

    @Test
    void shouldFilterReportsByStatus() {
        UUID workId = UUID.randomUUID();

        ModerationReport openReport = new ModerationReport(
                workId,
                UUID.randomUUID(),
                ReportReason.COPYRIGHT,
                "Open copyright report."
        );

        ModerationReport underReviewReport = new ModerationReport(
                workId,
                UUID.randomUUID(),
                ReportReason.AGE_RATING,
                "Report under review."
        );

        repository.saveAllAndFlush(
                List.of(openReport, underReviewReport)
        );

        jdbcTemplate.update(
                """
                UPDATE moderation_report
                SET status = ?
                WHERE id = ?
                """,
                ReportStatus.UNDER_REVIEW.name(),
                underReviewReport.getId()
        );

        entityManager.clear();

        Page<ModerationReport> result = repository.findByStatus(
                ReportStatus.UNDER_REVIEW,
                Pageable.unpaged(REPORT_ORDER)
        );

        assertThat(result.getContent())
                .extracting(ModerationReport::getId)
                .contains(underReviewReport.getId())
                .doesNotContain(openReport.getId());

        assertThat(result.getContent())
                .allMatch(report ->
                        report.getStatus() == ReportStatus.UNDER_REVIEW
                );
    }

    @Test
    void shouldOrderReportsByIdWhenCreatedAtIsEqual() {
        UUID workId = UUID.randomUUID();

        ModerationReport first = new ModerationReport(
                workId,
                UUID.randomUUID(),
                ReportReason.COPYRIGHT,
                "First report."
        );

        ModerationReport second = new ModerationReport(
                workId,
                UUID.randomUUID(),
                ReportReason.OTHER,
                "Second report."
        );

        repository.saveAllAndFlush(
                List.of(first, second)
        );

        Timestamp sameTimestamp = Timestamp.from(
                Instant.parse("2026-01-01T12:00:00Z")
        );

        jdbcTemplate.update(
                """
                UPDATE moderation_report
                SET created_at = ?
                WHERE id IN (?, ?)
                """,
                sameTimestamp,
                first.getId(),
                second.getId()
        );

        entityManager.clear();

        List<UUID> orderedIds = repository.findAll(REPORT_ORDER)
                .stream()
                .map(ModerationReport::getId)
                .toList();

        List<UUID> expectedOrder = jdbcTemplate.queryForList(
                """
                SELECT id
                FROM moderation_report
                WHERE id IN (?, ?)
                ORDER BY id ASC
                """,
                UUID.class,
                first.getId(),
                second.getId()
        );

        assertThat(expectedOrder).hasSize(2);

        assertThat(orderedIds)
                .contains(first.getId(), second.getId());

        assertThat(orderedIds.indexOf(expectedOrder.get(0)))
                .isLessThan(
                        orderedIds.indexOf(expectedOrder.get(1))
                );
    }

    @Test
    void shouldPersistResolvedReportDecision() {
        ModerationReport report = new ModerationReport(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReportReason.COPYRIGHT,
                "Possible copyright infringement."
        );

        report.startReview();
        report.resolveNoViolation();

        repository.saveAndFlush(report);

        entityManager.clear();

        ModerationReport persisted = repository
                .findById(report.getId())
                .orElseThrow();

        assertThat(persisted.getStatus())
                .isEqualTo(ReportStatus.RESOLVED);

        assertThat(persisted.getDecision())
                .isEqualTo(ModerationDecision.NO_VIOLATION);

        String storedDecision = jdbcTemplate.queryForObject(
                """
                SELECT decision
                FROM moderation_report
                WHERE id = ?
                """,
                String.class,
                report.getId()
        );

        assertThat(storedDecision)
                .isEqualTo("NO_VIOLATION");
    }

    @Test
    void shouldRejectResolvedReportWithoutDecisionAtDatabaseLevel() {
        ModerationReport report = new ModerationReport(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReportReason.COPYRIGHT,
                "Possible copyright infringement."
        );

        repository.saveAndFlush(report);

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        """
                        UPDATE moderation_report
                        SET status = 'RESOLVED',
                            decision = NULL
                        WHERE id = ?
                        """,
                        report.getId()
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}