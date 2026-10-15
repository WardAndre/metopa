package metopa.catalog;

import java.util.Set;
import java.util.UUID;

public record WorkDetails(
        UUID id,
        String title,
        String description,
        WorkType type,
        ReadingDirection readingDirection,
        PresentationMode presentationMode,
        Set<Genre> genres,
        boolean hasCover
) {

    public WorkDetails {
        genres = Set.copyOf(genres);
    }

    public WorkDetails(
            UUID id,
            String title,
            String description,
            WorkType type,
            ReadingDirection readingDirection,
            PresentationMode presentationMode,
            boolean hasCover
    ) {
        this(
                id,
                title,
                description,
                type,
                readingDirection,
                presentationMode,
                Set.of(),
                hasCover
        );
    }
}