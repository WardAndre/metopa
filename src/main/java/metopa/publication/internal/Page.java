package metopa.publication.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "installment_page")
public class Page {

    @Id
    private UUID id;

    @Column(name = "installment_id", nullable = false)
    private UUID installmentId;

    @Column(name = "page_number", nullable = false)
    private int number;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Page() {
    }

    public Page(
            UUID installmentId,
            int number,
            String storageKey,
            String contentType
    ) {
        this.id = UUID.randomUUID();
        this.installmentId = installmentId;
        this.number = number;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    UUID getInstallmentId() {
        return installmentId;
    }

    int getNumber() {
        return number;
    }

    String getStorageKey() {
        return storageKey;
    }

    String getContentType() {
        return contentType;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}