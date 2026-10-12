package metopa.publication;

import metopa.catalog.WorkType;

import java.util.UUID;

public record PublishedWorkSummary(
        UUID id,
        String title,
        WorkType type,
        boolean hasCover
) {
}