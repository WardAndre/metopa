package metopa.reading.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reading_progress")
public class ReadingProgress {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "work_id", nullable = false)
    private UUID workId;

    @Column(name = "installment_id", nullable = false)
    private UUID installmentId;

    @Column(name = "page_id", nullable = false)
    private UUID pageId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReadingProgress() {
    }

    public ReadingProgress(
            UUID userId,
            UUID workId,
            UUID installmentId,
            UUID pageId
    ) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.workId = workId;
        this.installmentId = installmentId;
        this.pageId = pageId;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getWorkId() {
        return workId;
    }

    public UUID getInstallmentId() {
        return installmentId;
    }

    public UUID getPageId() {
        return pageId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updatePosition(
            UUID installmentId,
            UUID pageId
    ) {
        this.installmentId = installmentId;
        this.pageId = pageId;
        this.updatedAt = Instant.now();
    }
}