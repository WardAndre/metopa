package metopa.moderation;

import metopa.catalog.CatalogService;
import metopa.moderation.internal.ModerationAuditAction;
import metopa.moderation.internal.ModerationAuditEntry;
import metopa.moderation.internal.ModerationAuditEntryRepository;
import metopa.moderation.internal.ModerationReport;
import metopa.moderation.internal.ModerationReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModerationServiceResolveTests {

    @Mock
    private ModerationReportRepository reportRepository;

    @Mock
    private ModerationAuditEntryRepository auditRepository;

    @Mock
    private CatalogService catalogService;

    @InjectMocks
    private ModerationService service;

    @Test
    void shouldResolveReviewedReportAndRecordAudit() {
        ModerationReport report = createReport();
        report.startReview();

        UUID adminUserId = UUID.randomUUID();

        when(reportRepository.findByIdForUpdate(report.getId()))
                .thenReturn(Optional.of(report));

        service.resolveNoViolation(
                report.getId(),
                adminUserId,
                "  No copyright violation was found.  "
        );

        assertThat(report.getStatus())
                .isEqualTo(ReportStatus.RESOLVED);

        assertThat(report.getDecision())
                .isEqualTo(ModerationDecision.NO_VIOLATION);

        ArgumentCaptor<ModerationAuditEntry> captor =
                ArgumentCaptor.forClass(ModerationAuditEntry.class);

        verify(auditRepository).save(captor.capture());

        ModerationAuditEntry audit = captor.getValue();

        assertThat(audit.getReportId())
                .isEqualTo(report.getId());

        assertThat(audit.getAdminUserId())
                .isEqualTo(adminUserId);

        assertThat(audit.getAction())
                .isEqualTo(ModerationAuditAction.REPORT_RESOLVED);

        assertThat(audit.getNote())
                .isEqualTo("No copyright violation was found.");

        verify(reportRepository)
                .findByIdForUpdate(report.getId());

        verifyNoInteractions(catalogService);
    }

    @Test
    void shouldRejectResolvingMissingReport() {
        UUID reportId = UUID.randomUUID();

        when(reportRepository.findByIdForUpdate(reportId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.resolveNoViolation(
                        reportId,
                        UUID.randomUUID(),
                        "No violation was found."
                )
        ).isInstanceOf(NoSuchElementException.class);

        verifyNoInteractions(auditRepository);
    }

    @Test
    void shouldRejectResolvingOpenReport() {
        ModerationReport report = createReport();

        when(reportRepository.findByIdForUpdate(report.getId()))
                .thenReturn(Optional.of(report));

        assertThatThrownBy(() ->
                service.resolveNoViolation(
                        report.getId(),
                        UUID.randomUUID(),
                        "No violation was found."
                )
        ).isInstanceOf(IllegalStateException.class);

        assertThat(report.getStatus())
                .isEqualTo(ReportStatus.OPEN);

        assertThat(report.getDecision()).isNull();

        verifyNoInteractions(auditRepository);
    }

    @Test
    void shouldRejectResolvingReportTwice() {
        ModerationReport report = createReport();

        report.startReview();
        report.resolveNoViolation();

        when(reportRepository.findByIdForUpdate(report.getId()))
                .thenReturn(Optional.of(report));

        assertThatThrownBy(() ->
                service.resolveNoViolation(
                        report.getId(),
                        UUID.randomUUID(),
                        "Second resolution attempt."
                )
        ).isInstanceOf(IllegalStateException.class);

        assertThat(report.getStatus())
                .isEqualTo(ReportStatus.RESOLVED);

        assertThat(report.getDecision())
                .isEqualTo(ModerationDecision.NO_VIOLATION);

        verifyNoInteractions(auditRepository);
    }

    @Test
    void shouldRejectBlankNoteWithoutChangingReport() {
        ModerationReport report = createReport();
        report.startReview();

        when(reportRepository.findByIdForUpdate(report.getId()))
                .thenReturn(Optional.of(report));

        assertThatThrownBy(() ->
                service.resolveNoViolation(
                        report.getId(),
                        UUID.randomUUID(),
                        "   "
                )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThat(report.getStatus())
                .isEqualTo(ReportStatus.UNDER_REVIEW);

        assertThat(report.getDecision()).isNull();

        verifyNoInteractions(auditRepository);
    }

    @Test
    void shouldRejectMissingAdministratorBeforeQuery() {
        assertThatThrownBy(() ->
                service.resolveNoViolation(
                        UUID.randomUUID(),
                        null,
                        "No violation was found."
                )
        ).isInstanceOf(NullPointerException.class);

        verifyNoInteractions(
                reportRepository,
                auditRepository,
                catalogService
        );
    }

    @Test
    void shouldRejectMissingReportIdBeforeQuery() {
        assertThatThrownBy(() ->
                service.resolveNoViolation(
                        null,
                        UUID.randomUUID(),
                        "No violation was found."
                )
        ).isInstanceOf(NullPointerException.class);

        verifyNoInteractions(
                reportRepository,
                auditRepository,
                catalogService
        );
    }

    private ModerationReport createReport() {
        return new ModerationReport(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReportReason.COPYRIGHT,
                "Possible copyright infringement."
        );
    }
}
