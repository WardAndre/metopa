package metopa.library.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "library_entry")
public class LibraryEntry {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "work_id", nullable = false)
    private UUID workId;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    protected LibraryEntry() {
    }

    public LibraryEntry(
            UUID userId,
            UUID workId
    ) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.workId = workId;
        this.addedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    UUID getWorkId() {
        return workId;
    }

    Instant getAddedAt() {
        return addedAt;
    }
}