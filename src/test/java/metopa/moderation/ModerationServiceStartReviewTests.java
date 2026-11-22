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
class ModerationServiceStartReviewTests {

    @Mock
    private ModerationReportRepository reportRepository;

    @Mock
    private ModerationAuditEntryRepository auditRepository;

    @Mock
    private CatalogService catalogService;

    @InjectMocks
    private ModerationService service;

    @Test
    void shouldStartReviewAndRecordAuditEntry() {
        ModerationReport report = createReport();

        UUID adminUserId = UUID.randomUUID();

        when(reportRepository.findByIdForUpdate(report.getId()))
                .thenReturn(Optional.of(report));

        service.startReview(
                report.getId(),
                adminUserId,
                "  Copyright review initiated.  "
        );

        assertThat(report.getStatus())
                .isEqualTo(ReportStatus.UNDER_REVIEW);

        ArgumentCaptor<ModerationAuditEntry> captor =
                ArgumentCaptor.forClass(ModerationAuditEntry.class);

        verify(auditRepository).save(captor.capture());

        ModerationAuditEntry audit = captor.getValue();

        assertThat(audit.getReportId())
                .isEqualTo(report.getId());

        assertThat(audit.getAdminUserId())
                .isEqualTo(adminUserId);

        assertThat(audit.getAction())
                .isEqualTo(ModerationAuditAction.REVIEW_STARTED);

        assertThat(audit.getNote())
                .isEqualTo("Copyright review initiated.");

        verify(reportRepository)
                .findByIdForUpdate(report.getId());

        verifyNoInteractions(catalogService);
    }

    @Test
    void shouldRejectStartingReviewForMissingReport() {
        UUID reportId = UUID.randomUUID();

        when(reportRepository.findByIdForUpdate(reportId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.startReview(
                        reportId,
                        UUID.randomUUID(),
                        "Review initiated."
                )
        ).isInstanceOf(NoSuchElementException.class);

        verifyNoInteractions(auditRepository);
    }

    @Test
    void shouldRejectStartingReviewTwice() {
        ModerationReport report = createReport();

        report.startReview();

        when(reportRepository.findByIdForUpdate(report.getId()))
                .thenReturn(Optional.of(report));

        assertThatThrownBy(() ->
                service.startReview(
                        report.getId(),
                        UUID.randomUUID(),
                        "Second review attempt."
                )
        ).isInstanceOf(IllegalStateException.class);

        assertThat(report.getStatus())
                .isEqualTo(ReportStatus.UNDER_REVIEW);

        verifyNoInteractions(auditRepository);
    }

    @Test
    void shouldRejectBlankNoteWithoutChangingReport() {
        ModerationReport report = createReport();

        when(reportRepository.findByIdForUpdate(report.getId()))
                .thenReturn(Optional.of(report));

        assertThatThrownBy(() ->
                service.startReview(
                        report.getId(),
                        UUID.randomUUID(),
                        "   "
                )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThat(report.getStatus())
                .isEqualTo(ReportStatus.OPEN);

        verifyNoInteractions(auditRepository);
    }

    @Test
    void shouldRejectMissingAdministratorBeforeQuery() {
        ModerationReport report = createReport();

        assertThatThrownBy(() ->
                service.startReview(
                        report.getId(),
                        null,
                        "Review initiated."
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
