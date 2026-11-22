
package metopa.moderation;

import metopa.catalog.CatalogService;
import metopa.catalog.WorkNotFoundException;
import metopa.moderation.internal.ModerationReport;
import metopa.moderation.internal.ModerationReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTests {

    @Mock
    private CatalogService catalogService;

    @Mock
    private ModerationReportRepository repository;

    @InjectMocks
    private ModerationService service;

    @Test
    void shouldCreateReportForExistingWork() {
        UUID workId = UUID.randomUUID();
        UUID reporterUserId = UUID.randomUUID();

        CreateReportCommand command = new CreateReportCommand(
                reporterUserId,
                workId,
                ReportReason.COPYRIGHT,
                "Unauthorized reproduction of copyrighted content."
        );

        UUID reportId = service.createReport(command);

        verify(catalogService).findWork(workId);

        ArgumentCaptor<ModerationReport> captor =
                ArgumentCaptor.forClass(ModerationReport.class);

        verify(repository).save(captor.capture());

        ModerationReport saved = captor.getValue();

        assertThat(reportId).isEqualTo(saved.getId());
        assertThat(saved.getWorkId()).isEqualTo(workId);
        assertThat(saved.getReporterUserId())
                .isEqualTo(reporterUserId);
        assertThat(saved.getReason())
                .isEqualTo(ReportReason.COPYRIGHT);
        assertThat(saved.getDescription())
                .isEqualTo(command.description());
        assertThat(saved.getStatus())
                .isEqualTo(ReportStatus.OPEN);
    }

    @Test
    void shouldRejectReportForNonexistentWork() {
        UUID workId = UUID.randomUUID();

        CreateReportCommand command = new CreateReportCommand(
                UUID.randomUUID(),
                workId,
                ReportReason.COPYRIGHT,
                "Possible copyright infringement."
        );

        when(catalogService.findWork(workId))
                .thenThrow(new WorkNotFoundException(workId));

        assertThatThrownBy(() -> service.createReport(command))
                .isInstanceOf(WorkNotFoundException.class);

        verify(catalogService).findWork(workId);
        verifyNoInteractions(repository);
    }

    @Test
    void shouldRejectBlankDescription() {
        CreateReportCommand command = new CreateReportCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReportReason.OTHER,
                "   "
        );

        assertThatThrownBy(() -> service.createReport(command))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(catalogService, repository);
    }

    @Test
    void shouldRejectDescriptionLongerThan2000Characters() {
        CreateReportCommand command = new CreateReportCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReportReason.OTHER,
                "a".repeat(2001)
        );

        assertThatThrownBy(() -> service.createReport(command))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(catalogService, repository);
    }

    @Test
    void shouldRejectMissingReporterId() {
        CreateReportCommand command = new CreateReportCommand(
                null,
                UUID.randomUUID(),
                ReportReason.OTHER,
                "Report description."
        );

        assertThatThrownBy(() -> service.createReport(command))
                .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(catalogService, repository);
    }

    @Test
    void shouldFindAllReportsWithPagination() {
        ModerationReport report = new ModerationReport(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReportReason.COPYRIGHT,
                "Possible copyright infringement."
        );

        Page<ModerationReport> repositoryPage =
                new PageImpl<>(
                        List.of(report),
                        PageRequest.of(0, 20),
                        1
                );

        when(repository.findAll(any(Pageable.class)))
                .thenReturn(repositoryPage);

        Page<ModerationReportSummary> result =
                service.findReports(null, 0, 20);

        assertThat(result.getNumber()).isZero();
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getTotalElements()).isEqualTo(1);

        assertThat(result.getContent()).hasSize(1);

        ModerationReportSummary summary =
                result.getContent().getFirst();

        assertThat(summary.id()).isEqualTo(report.getId());
        assertThat(summary.workId()).isEqualTo(report.getWorkId());
        assertThat(summary.reason()).isEqualTo(report.getReason());
        assertThat(summary.description())
                .isEqualTo(report.getDescription());
        assertThat(summary.status()).isEqualTo(ReportStatus.OPEN);
        assertThat(summary.createdAt())
                .isEqualTo(report.getCreatedAt());

        ArgumentCaptor<Pageable> captor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(repository).findAll(captor.capture());

        Pageable pageable = captor.getValue();

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);

        assertThat(pageable.getSort().getOrderFor("createdAt"))
                .isEqualTo(Sort.Order.desc("createdAt"));

        assertThat(pageable.getSort().getOrderFor("id"))
                .isEqualTo(Sort.Order.asc("id"));

        verifyNoInteractions(catalogService);
    }

    @Test
    void shouldFilterReportsByStatus() {
        ModerationReport report = new ModerationReport(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReportReason.AGE_RATING,
                "Incorrect age classification."
        );

        Page<ModerationReport> repositoryPage =
                new PageImpl<>(
                        List.of(report),
                        PageRequest.of(1, 10),
                        11
                );

        when(repository.findByStatus(
                eq(ReportStatus.OPEN),
                any(Pageable.class)
        )).thenReturn(repositoryPage);

        Page<ModerationReportSummary> result =
                service.findReports(ReportStatus.OPEN, 1, 10);

        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getTotalElements()).isEqualTo(11);

        assertThat(result.getContent())
                .hasSize(1)
                .allMatch(summary ->
                        summary.status() == ReportStatus.OPEN
                );

        ArgumentCaptor<Pageable> captor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(repository).findByStatus(
                eq(ReportStatus.OPEN),
                captor.capture()
        );

        assertThat(captor.getValue().getPageNumber())
                .isEqualTo(1);

        assertThat(captor.getValue().getPageSize())
                .isEqualTo(10);

        verifyNoInteractions(catalogService);
    }

    @Test
    void shouldRejectNegativePage() {
        assertThatThrownBy(() ->
                service.findReports(null, -1, 20)
        ).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(catalogService, repository);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 51})
    void shouldRejectInvalidPageSize(int size) {
        assertThatThrownBy(() ->
                service.findReports(null, 0, size)
        ).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(catalogService, repository);
    }
}
