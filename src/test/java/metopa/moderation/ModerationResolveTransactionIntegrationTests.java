package metopa.moderation;

import metopa.moderation.internal.ModerationAuditAction;
import metopa.moderation.internal.ModerationAuditEntry;
import metopa.moderation.internal.ModerationAuditEntryRepository;
import metopa.moderation.internal.ModerationReport;
import metopa.moderation.internal.ModerationReportRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class ModerationResolveTransactionIntegrationTests {

    @Autowired
    private ModerationService moderationService;

    @Autowired
    private ModerationReportRepository reportRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private ModerationAuditEntryRepository auditRepository;

    private UUID createdReportId;

    @AfterEach
    void cleanUp() {
        if (createdReportId == null) {
            return;
        }

        jdbcTemplate.update(
                """
                DELETE FROM moderation_audit_entry
                WHERE report_id = ?
                """,
                createdReportId
        );

        jdbcTemplate.update(
                """
                DELETE FROM moderation_report
                WHERE id = ?
                """,
                createdReportId
        );
    }

    @Test
    void shouldCommitResolutionDecisionAndAuditTogether() {
        UUID reportId = createReportUnderReview();
        UUID resolvingAdminId = UUID.randomUUID();

        moderationService.resolveNoViolation(
                reportId,
                resolvingAdminId,
                "  No copyright violation was found.  "
        );

        assertThat(findStatus(reportId))
                .isEqualTo("RESOLVED");

        assertThat(findDecision(reportId))
                .isEqualTo("NO_VIOLATION");

        assertThat(countAuditEntries(
                reportId,
                ModerationAuditAction.REVIEW_STARTED
        )).isEqualTo(1);

        assertThat(countAuditEntries(
                reportId,
                ModerationAuditAction.REPORT_RESOLVED
        )).isEqualTo(1);

        UUID recordedAdminId = jdbcTemplate.queryForObject(
                """
                SELECT admin_user_id
                FROM moderation_audit_entry
                WHERE report_id = ?
                  AND action = 'REPORT_RESOLVED'
                """,
                UUID.class,
                reportId
        );

        assertThat(recordedAdminId)
                .isEqualTo(resolvingAdminId);

        String note = jdbcTemplate.queryForObject(
                """
                SELECT note
                FROM moderation_audit_entry
                WHERE report_id = ?
                  AND action = 'REPORT_RESOLVED'
                """,
                String.class,
                reportId
        );

        assertThat(note)
                .isEqualTo("No copyright violation was found.");
    }

    @Test
    void shouldRollbackResolutionWhenAuditPersistenceFails() {
        UUID reportId = createReportUnderReview();

        doThrow(new DataIntegrityViolationException(
                "Simulated resolution audit failure."
        )).when(auditRepository).save(
                argThat(entry ->
                        entry != null
                                && entry.getAction()
                                == ModerationAuditAction.REPORT_RESOLVED
                )
        );

        assertThatThrownBy(() ->
                moderationService.resolveNoViolation(
                        reportId,
                        UUID.randomUUID(),
                        "No violation was found."
                )
        ).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(findStatus(reportId))
                .isEqualTo("UNDER_REVIEW");

        assertThat(findDecision(reportId))
                .isNull();

        assertThat(countAuditEntries(
                reportId,
                ModerationAuditAction.REVIEW_STARTED
        )).isEqualTo(1);

        assertThat(countAuditEntries(
                reportId,
                ModerationAuditAction.REPORT_RESOLVED
        )).isZero();
    }

    @Test
    void shouldRejectDuplicateResolutionWithoutNewAudit() {
        UUID reportId = createReportUnderReview();

        moderationService.resolveNoViolation(
                reportId,
                UUID.randomUUID(),
                "No copyright violation was found."
        );

        assertThatThrownBy(() ->
                moderationService.resolveNoViolation(
                        reportId,
                        UUID.randomUUID(),
                        "Second resolution attempt."
                )
        ).isInstanceOf(IllegalStateException.class);

        assertThat(findStatus(reportId))
                .isEqualTo("RESOLVED");

        assertThat(findDecision(reportId))
                .isEqualTo("NO_VIOLATION");

        assertThat(countAuditEntries(
                reportId,
                ModerationAuditAction.REVIEW_STARTED
        )).isEqualTo(1);

        assertThat(countAuditEntries(
                reportId,
                ModerationAuditAction.REPORT_RESOLVED
        )).isEqualTo(1);
    }

    private UUID createReportUnderReview() {
        ModerationReport report = new ModerationReport(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReportReason.COPYRIGHT,
                "Possible copyright infringement."
        );

        reportRepository.saveAndFlush(report);

        createdReportId = report.getId();

        moderationService.startReview(
                createdReportId,
                UUID.randomUUID(),
                "Administrative review initiated."
        );

        return createdReportId;
    }

    private String findStatus(UUID reportId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM moderation_report
                WHERE id = ?
                """,
                String.class,
                reportId
        );
    }

    private String findDecision(UUID reportId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT decision
                FROM moderation_report
                WHERE id = ?
                """,
                String.class,
                reportId
        );
    }

    private long countAuditEntries(
            UUID reportId,
            ModerationAuditAction action
    ) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM moderation_audit_entry
                WHERE report_id = ?
                  AND action = ?
                """,
                Long.class,
                reportId,
                action.name()
        );

        return count;
    }
}
