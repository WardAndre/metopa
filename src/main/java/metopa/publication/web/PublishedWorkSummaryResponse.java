package metopa.publication.web;

import metopa.catalog.WorkType;
import metopa.catalog.Genre;
import metopa.publication.PublishedWorkSummary;

import java.util.UUID;
import java.util.Set;

record PublishedWorkSummaryResponse(
        UUID id,
        String title,
        WorkType type,
        Set<Genre> genres,
        String coverUrl
) {

    static PublishedWorkSummaryResponse from(
            PublishedWorkSummary work
    ) {
        return new PublishedWorkSummaryResponse(
                work.id(),
                work.title(),
                work.type(),
                work.genres(),
                work.hasCover()
                        ? "/api/works/"
                        + work.id()
                        + "/cover"
                        : null
        );
    }
}