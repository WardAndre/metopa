package metopa.moderation.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "moderation_audit_entry")
public class ModerationAuditEntry {

    @Id
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "admin_user_id", nullable = false)
    private UUID adminUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ModerationAuditAction action;

    @Column(nullable = false, length = 2000)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ModerationAuditEntry() {
    }

    public ModerationAuditEntry(
            UUID reportId,
            UUID adminUserId,
            ModerationAuditAction action,
            String note
    ) {
        Objects.requireNonNull(reportId, "reportId is required");
        Objects.requireNonNull(adminUserId, "adminUserId is required");
        Objects.requireNonNull(action, "action is required");

        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException(
                    "Audit note is required."
            );
        }

        String normalizedNote = note.strip();

        if (normalizedNote.length() > 2000) {
            throw new IllegalArgumentException(
                    "Audit note must not exceed 2000 characters."
            );
        }

        this.id = UUID.randomUUID();
        this.reportId = reportId;
        this.adminUserId = adminUserId;
        this.action = action;
        this.note = normalizedNote;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getReportId() {
        return reportId;
    }

    public UUID getAdminUserId() {
        return adminUserId;
    }

    public ModerationAuditAction getAction() {
        return action;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
