package metopa.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import metopa.catalog.PresentationMode;
import metopa.catalog.ReadingDirection;
import metopa.catalog.WorkType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "work")
public class Work {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", nullable = false, length = 30)
    private WorkType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "reading_direction", nullable = false, length = 30)
    private ReadingDirection readingDirection;

    @Enumerated(EnumType.STRING)
    @Column(name = "presentation_mode", nullable = false, length = 30)
    private PresentationMode presentationMode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Work() {
    }

    public Work(
            UUID ownerId,
            String title,
            String description,
            WorkType type,
            ReadingDirection readingDirection,
            PresentationMode presentationMode
    ) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.title = title;
        this.description = description;
        this.type = type;
        this.readingDirection = readingDirection;
        this.presentationMode = presentationMode;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getTitle() {
        return title;
    }

    String getDescription() {
        return description;
    }

    public WorkType getType() {
        return type;
    }

    ReadingDirection getReadingDirection() {
        return readingDirection;
    }

    PresentationMode getPresentationMode() {
        return presentationMode;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}