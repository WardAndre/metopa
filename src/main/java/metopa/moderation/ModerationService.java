package metopa.moderation;

import metopa.catalog.CatalogService;
import metopa.moderation.internal.ModerationAuditAction;
import metopa.moderation.internal.ModerationAuditEntry;
import metopa.moderation.internal.ModerationAuditEntryRepository;
import metopa.moderation.internal.ModerationReport;
import metopa.moderation.internal.ModerationReportRepository;
import metopa.moderation.ModerationDecision;
import metopa.moderation.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@Service
public class ModerationService {

    private static final int MAX_PAGE_SIZE = 50;

    private static final Sort REPORT_ORDER = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.asc("id")
    );

    private final ModerationReportRepository repository;
    private final CatalogService catalogService;
    private final ModerationAuditEntryRepository auditRepository;

    public ModerationService(
            ModerationReportRepository repository,
            CatalogService catalogService,
            ModerationAuditEntryRepository auditRepository
    ) {
        this.repository = repository;
        this.catalogService = catalogService;
        this.auditRepository = auditRepository;
    }

    @Transactional
    public UUID createReport(CreateReportCommand command) {
        Objects.requireNonNull(
                command,
                "command is required"
        );

        ModerationReport report = new ModerationReport(
                command.workId(),
                command.reporterUserId(),
                command.reason(),
                command.description()
        );

        catalogService.findWork(command.workId());

        repository.save(report);

        return report.getId();
    }

    @Transactional(readOnly = true)
    public Page<ModerationReportSummary> findReports(
            ReportStatus status,
            int page,
            int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page must not be negative."
            );
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and 50."
            );
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                REPORT_ORDER
        );

        Page<ModerationReport> reports =
                status == null
                        ? repository.findAll(pageable)
                        : repository.findByStatus(status, pageable);

        return reports.map(report ->
                new ModerationReportSummary(
                        report.getId(),
                        report.getWorkId(),
                        report.getReason(),
                        report.getDescription(),
                        report.getStatus(),
                        report.getCreatedAt()
                )
        );
    }

    @Transactional
    public void startReview(
            UUID reportId,
            UUID adminUserId,
            String note
    ) {
        Objects.requireNonNull(
                reportId,
                "reportId is required"
        );

        Objects.requireNonNull(
                adminUserId,
                "adminUserId is required"
        );

        ModerationReport report = repository
                .findByIdForUpdate(reportId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Moderation report not found."
                        )
                );

        ModerationAuditEntry auditEntry =
                new ModerationAuditEntry(
                        reportId,
                        adminUserId,
                        ModerationAuditAction.REVIEW_STARTED,
                        note
                );

        report.startReview();

        auditRepository.save(auditEntry);
    }

    @Transactional
    public void resolveNoViolation(
            UUID reportId,
            UUID adminUserId,
            String note
    ) {
        Objects.requireNonNull(
                reportId,
                "reportId is required"
        );

        Objects.requireNonNull(
                adminUserId,
                "adminUserId is required"
        );

        ModerationReport report = repository
                .findByIdForUpdate(reportId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Moderation report not found."
                        )
                );

        ModerationAuditEntry auditEntry =
                new ModerationAuditEntry(
                        reportId,
                        adminUserId,
                        ModerationAuditAction.REPORT_RESOLVED,
                        note
                );

        report.resolveNoViolation();

        auditRepository.save(auditEntry);
    }

    @Transactional
    public void resolveWorkRestriction(
            UUID reportId,
            UUID adminUserId,
            String note
    ) {
        Objects.requireNonNull(
                reportId,
                "reportId is required"
        );

        Objects.requireNonNull(
                adminUserId,
                "adminUserId is required"
        );

        ModerationReport report = repository
                .findByIdForUpdate(reportId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Moderation report not found."
                        )
                );

        ModerationAuditEntry auditEntry =
                new ModerationAuditEntry(
                        reportId,
                        adminUserId,
                        ModerationAuditAction.REPORT_RESOLVED,
                        note
                );

        report.resolveWorkRestriction();

        catalogService.restrictWork(
                report.getWorkId()
        );

        auditRepository.save(auditEntry);
    }

    @Transactional
    public void restoreWork(
            UUID reportId,
            UUID adminUserId,
            String note
    ) {
        Objects.requireNonNull(
                reportId,
                "reportId is required"
        );

        Objects.requireNonNull(
                adminUserId,
                "adminUserId is required"
        );

        ModerationReport report = repository
                .findByIdForUpdate(reportId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Moderation report not found."
                        )
                );

        if (report.getStatus() != ReportStatus.RESOLVED
                || report.getDecision()
                != ModerationDecision.WORK_RESTRICTED) {
            throw new IllegalStateException(
                    "Only reports resolved with a work restriction can be restored."
            );
        }

        ModerationAuditEntry auditEntry =
                new ModerationAuditEntry(
                        reportId,
                        adminUserId,
                        ModerationAuditAction.WORK_RESTORED,
                        note
                );

        catalogService.restoreWork(report.getWorkId());

        auditRepository.save(auditEntry);
    }
}
