package metopa.publication;

import metopa.catalog.Genre;
import metopa.catalog.WorkType;

import java.util.Set;
import java.util.UUID;

public record PublishedWorkSummary(
        UUID id,
        String title,
        WorkType type,
        Set<Genre> genres,
        boolean hasCover
) {

    public PublishedWorkSummary {
        genres = Set.copyOf(genres);
    }

    public PublishedWorkSummary(
            UUID id,
            String title,
            WorkType type,
            boolean hasCover
    ) {
        this(
                id,
                title,
                type,
                Set.of(),
                hasCover
        );
    }
}