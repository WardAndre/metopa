package metopa.moderation.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import metopa.moderation.ReportReason;
import metopa.moderation.ReportStatus;
import metopa.moderation.ModerationDecision;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "moderation_report")
public class ModerationReport {

    @Id
    private UUID id;

    @Column(name = "work_id", nullable = false)
    private UUID workId;

    @Column(name = "reporter_user_id", nullable = false)
    private UUID reporterUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReportReason reason;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 32)
    private ModerationDecision decision;

    protected ModerationReport() {
    }

    public ModerationReport(
            UUID workId,
            UUID reporterUserId,
            ReportReason reason,
            String description
    ) {
        Objects.requireNonNull(workId, "workId is required");
        Objects.requireNonNull(reporterUserId, "reporterUserId is required");
        Objects.requireNonNull(reason, "reason is required");

        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "Report description is required."
            );
        }

        String normalizedDescription = description.strip();

        if (normalizedDescription.length() > 2000) {
            throw new IllegalArgumentException(
                    "Report description must not exceed 2000 characters."
            );
        }

        this.id = UUID.randomUUID();
        this.workId = workId;
        this.reporterUserId = reporterUserId;
        this.reason = reason;
        this.description = normalizedDescription;
        this.status = ReportStatus.OPEN;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkId() {
        return workId;
    }

    public UUID getReporterUserId() {
        return reporterUserId;
    }

    public ReportReason getReason() {
        return reason;
    }

    public String getDescription() {
        return description;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void startReview() {
        if (status != ReportStatus.OPEN) {
            throw new IllegalStateException(
                    "Only open reports can enter review."
            );
        }

        this.status = ReportStatus.UNDER_REVIEW;
        this.updatedAt = Instant.now();
    }

    public ModerationDecision getDecision() {
        return decision;
    }

    public void resolveNoViolation() {
        if (status != ReportStatus.UNDER_REVIEW) {
            throw new IllegalStateException(
                    "Only reports under review can be resolved."
            );
        }

        this.decision = ModerationDecision.NO_VIOLATION;
        this.status = ReportStatus.RESOLVED;
        this.updatedAt = Instant.now();
    }

    public void resolveWorkRestriction() {
        if (status != ReportStatus.UNDER_REVIEW) {
            throw new IllegalStateException(
                    "Only reports under review can be resolved."
            );
        }

        this.decision = ModerationDecision.WORK_RESTRICTED;
        this.status = ReportStatus.RESOLVED;
        this.updatedAt = Instant.now();
    }
}