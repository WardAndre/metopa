package metopa.library;

import metopa.catalog.WorkType;

import java.time.Instant;
import java.util.UUID;

public record LibraryEntryView(
        UUID id,
        UUID workId,
        String title,
        WorkType type,
        Instant addedAt
) {
}