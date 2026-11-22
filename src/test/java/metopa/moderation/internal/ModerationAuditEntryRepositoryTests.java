package metopa.moderation.internal;

import jakarta.persistence.EntityManager;
import metopa.moderation.ReportReason;
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
class ModerationAuditEntryRepositoryTests {

    @Autowired
    private ModerationAuditEntryRepository auditRepository;

    @Autowired
    private ModerationReportRepository reportRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistAndLoadAuditEntry() {
        ModerationReport report = createReport();

        UUID adminUserId = UUID.randomUUID();

        ModerationAuditEntry entry = new ModerationAuditEntry(
                report.getId(),
                adminUserId,
                ModerationAuditAction.REVIEW_STARTED,
                "  Administrative review initiated.  "
        );

        auditRepository.save(entry);
        entityManager.flush();
        entityManager.clear();

        ModerationAuditEntry persisted = auditRepository
                .findById(entry.getId())
                .orElseThrow();

        assertThat(persisted.getId())
                .isEqualTo(entry.getId());

        assertThat(persisted.getReportId())
                .isEqualTo(report.getId());

        assertThat(persisted.getAdminUserId())
                .isEqualTo(adminUserId);

        assertThat(persisted.getAction())
                .isEqualTo(ModerationAuditAction.REVIEW_STARTED);

        assertThat(persisted.getNote())
                .isEqualTo("Administrative review initiated.");

        assertThat(persisted.getCreatedAt())
                .isNotNull();

        String storedAction = jdbcTemplate.queryForObject(
                """
                SELECT action
                FROM moderation_audit_entry
                WHERE id = ?
                """,
                String.class,
                entry.getId()
        );

        assertThat(storedAction)
                .isEqualTo("REVIEW_STARTED");
    }

    @Test
    void shouldRejectAuditEntryWithoutExistingReport() {
        UUID nonexistentReportId = UUID.randomUUID();

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        """
                        INSERT INTO moderation_audit_entry (
                            id,
                            report_id,
                            admin_user_id,
                            action,
                            note,
                            created_at
                        )
                        VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """,
                        UUID.randomUUID(),
                        nonexistentReportId,
                        UUID.randomUUID(),
                        ModerationAuditAction.REVIEW_STARTED.name(),
                        "Review should not be recorded."
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectInvalidAuditActionAtDatabaseLevel() {
        ModerationReport report = createReport();

        ModerationAuditEntry entry = new ModerationAuditEntry(
                report.getId(),
                UUID.randomUUID(),
                ModerationAuditAction.REVIEW_STARTED,
                "Review initiated."
        );

        auditRepository.save(entry);
        entityManager.flush();

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        """
                        UPDATE moderation_audit_entry
                        SET action = ?
                        WHERE id = ?
                        """,
                        "INVALID_ACTION",
                        entry.getId()
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldPreventDeletingReportWithAuditHistory() {
        ModerationReport report = createReport();

        ModerationAuditEntry entry = new ModerationAuditEntry(
                report.getId(),
                UUID.randomUUID(),
                ModerationAuditAction.REVIEW_STARTED,
                "Review initiated."
        );

        auditRepository.save(entry);
        entityManager.flush();

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        """
                        DELETE FROM moderation_report
                        WHERE id = ?
                        """,
                        report.getId()
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldPersistResolvedReportAuditAction() {
        ModerationReport report = createReport();

        ModerationAuditEntry entry = new ModerationAuditEntry(
                report.getId(),
                UUID.randomUUID(),
                ModerationAuditAction.REPORT_RESOLVED,
                "No violation was found."
        );

        auditRepository.save(entry);

        entityManager.flush();
        entityManager.clear();

        ModerationAuditEntry persisted = auditRepository
                .findById(entry.getId())
                .orElseThrow();

        assertThat(persisted.getAction())
                .isEqualTo(ModerationAuditAction.REPORT_RESOLVED);

        assertThat(persisted.getNote())
                .isEqualTo("No violation was found.");
    }

    private ModerationReport createReport() {
        ModerationReport report = new ModerationReport(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReportReason.COPYRIGHT,
                "Possible copyright infringement."
        );

        return reportRepository.saveAndFlush(report);
    }
}
