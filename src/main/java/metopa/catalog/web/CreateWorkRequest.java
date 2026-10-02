package metopa.catalog.web;

import metopa.catalog.PresentationMode;
import metopa.catalog.ReadingDirection;
import metopa.catalog.WorkType;

record CreateWorkRequest(
        String title,
        String description,
        WorkType type,
        ReadingDirection readingDirection,
        PresentationMode presentationMode
) {
}