package metopa.catalog.web;

import metopa.catalog.Genre;
import metopa.catalog.PresentationMode;
import metopa.catalog.ReadingDirection;
import metopa.catalog.WorkType;

import java.util.Set;

record UpdateWorkRequest(
        String title,
        String description,
        WorkType type,
        ReadingDirection readingDirection,
        PresentationMode presentationMode,
        Set<Genre> genres
) {

    UpdateWorkRequest {
        genres = genres == null
                ? Set.of()
                : Set.copyOf(genres);
    }
}