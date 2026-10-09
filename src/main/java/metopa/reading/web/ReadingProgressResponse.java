package metopa.reading.web;

import metopa.reading.ReadingProgressView;

import java.time.Instant;
import java.util.UUID;

record ReadingProgressResponse(
        UUID id,
        UUID workId,
        UUID installmentId,
        UUID pageId,
        Instant updatedAt
) {

    static ReadingProgressResponse from(
            ReadingProgressView view
    ) {
        return new ReadingProgressResponse(
                view.id(),
                view.workId(),
                view.installmentId(),
                view.pageId(),
                view.updatedAt()
        );
    }
}