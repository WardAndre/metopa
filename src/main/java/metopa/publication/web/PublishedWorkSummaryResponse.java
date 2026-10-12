package metopa.publication.web;

import metopa.catalog.WorkType;
import metopa.publication.PublishedWorkSummary;

import java.util.UUID;

record PublishedWorkSummaryResponse(
        UUID id,
        String title,
        WorkType type,
        String coverUrl
) {

    static PublishedWorkSummaryResponse from(
            PublishedWorkSummary work
    ) {
        return new PublishedWorkSummaryResponse(
                work.id(),
                work.title(),
                work.type(),
                work.hasCover()
                        ? "/api/works/"
                        + work.id()
                        + "/cover"
                        : null
        );
    }
}