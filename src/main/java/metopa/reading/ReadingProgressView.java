package metopa.reading;

import java.time.Instant;
import java.util.UUID;

public record ReadingProgressView(
        UUID id,
        UUID workId,
        UUID installmentId,
        UUID pageId,
        Instant updatedAt
) {
}