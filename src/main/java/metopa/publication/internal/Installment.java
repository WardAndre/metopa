package metopa.publication.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import metopa.publication.InstallmentType;
import metopa.publication.PublicationStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "installment")
public class Installment {

    @Id
    private UUID id;

    @Column(name = "work_id", nullable = false)
    private UUID workId;

    @Enumerated(EnumType.STRING)
    @Column(name = "installment_type", nullable = false, length = 30)
    private InstallmentType type;

    @Column(nullable = false)
    private int number;

    @Column(length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PublicationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Installment() {
    }

    public Installment(
            UUID workId,
            InstallmentType type,
            int number,
            String title
    ) {
        this.id = UUID.randomUUID();
        this.workId = workId;
        this.type = type;
        this.number = number;
        this.title = title;
        this.status = PublicationStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkId() {
        return workId;
    }

    public InstallmentType getType() {
        return type;
    }

    public int getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public PublicationStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    public void publish() {
        if (status == PublicationStatus.PUBLISHED) {
            return;
        }

        status = PublicationStatus.PUBLISHED;
        updatedAt = Instant.now();
    }
}