package metopa.catalog;

import java.util.Set;
import java.util.UUID;

public record WorkSummary(
        UUID id,
        String title,
        WorkType type,
        Set<Genre> genres,
        boolean hasCover
) {

    public WorkSummary {
        genres = Set.copyOf(genres);
    }

    public WorkSummary(
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