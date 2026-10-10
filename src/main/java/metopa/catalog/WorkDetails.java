package metopa.catalog;

import java.util.UUID;

public record WorkDetails(
        UUID id,
        String title,
        String description,
        WorkType type,
        ReadingDirection readingDirection,
        PresentationMode presentationMode
) {
}