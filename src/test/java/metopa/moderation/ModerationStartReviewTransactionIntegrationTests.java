package metopa.moderation;

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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class ModerationStartReviewTransactionIntegrationTests {

    @Autowired
    private ModerationService moderationService;

    @Autowired
    private ModerationReportRepository reportRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private ModerationAuditEntryRepository auditRepository;

    private UUID reportId;

    @AfterEach
    void cleanUp() {
        if (reportId == null) {
            return;
        }

        jdbcTemplate.update(
                "DELETE FROM moderation_audit_entry WHERE report_id = ?",
                reportId
        );

        jdbcTemplate.update(
                "DELETE FROM moderation_report WHERE id = ?",
                reportId
        );
    }

    @Test
    void shouldCommitReviewStatusAndAuditTogether() {
        UUID id = createReport();
        UUID adminUserId = UUID.randomUUID();

        moderationService.startReview(
                id,
                adminUserId,
                "  Copyright review initiated.  "
        );

        assertThat(findStatus(id))
                .isEqualTo(ReportStatus.UNDER_REVIEW.name());

        assertThat(countAuditEntries(id)).isEqualTo(1);

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                """
                SELECT admin_user_id, action, note
                FROM moderation_audit_entry
                WHERE report_id = ?
                """,
                id
        );

        assertThat(audit.get("admin_user_id"))
                .isEqualTo(adminUserId);

        assertThat(audit.get("action"))
                .isEqualTo("REVIEW_STARTED");

        assertThat(audit.get("note"))
                .isEqualTo("Copyright review initiated.");
    }

    @Test
    void shouldRollbackReviewWhenAuditPersistenceFails() {
        UUID id = createReport();

        doThrow(new DataIntegrityViolationException(
                "Simulated audit persistence failure."
        )).when(auditRepository)
                .save(any(ModerationAuditEntry.class));

        assertThatThrownBy(() ->
                moderationService.startReview(
                        id,
                        UUID.randomUUID(),
                        "Copyright review initiated."
                )
        ).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(findStatus(id))
                .isEqualTo(ReportStatus.OPEN.name());

        assertThat(countAuditEntries(id)).isZero();
    }

    @Test
    void shouldNotRecordDuplicateReviewStart() {
        UUID id = createReport();

        moderationService.startReview(
                id,
                UUID.randomUUID(),
                "First review initiated."
        );

        assertThatThrownBy(() ->
                moderationService.startReview(
                        id,
                        UUID.randomUUID(),
                        "Second review attempt."
                )
        ).isInstanceOf(IllegalStateException.class);

        assertThat(findStatus(id))
                .isEqualTo(ReportStatus.UNDER_REVIEW.name());

        assertThat(countAuditEntries(id)).isEqualTo(1);
    }

    private UUID createReport() {
        ModerationReport report = new ModerationReport(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReportReason.COPYRIGHT,
                "Possible copyright infringement."
        );

        reportRepository.saveAndFlush(report);

        reportId = report.getId();

        return reportId;
    }

    private String findStatus(UUID id) {
        return jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM moderation_report
                WHERE id = ?
                """,
                String.class,
                id
        );
    }

    private int countAuditEntries(UUID id) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM moderation_audit_entry
                WHERE report_id = ?
                """,
                Integer.class,
                id
        );

        return count;
    }
}
