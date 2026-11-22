package metopa.moderation.internal;

import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface ModerationAuditEntryRepository
        extends Repository<ModerationAuditEntry, UUID> {

    ModerationAuditEntry save(ModerationAuditEntry entry);

    Optional<ModerationAuditEntry> findById(UUID id);
}
