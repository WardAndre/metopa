package metopa.publication.web;

import metopa.catalog.WorkType;
import metopa.publication.PublishedWorkSummary;

import java.util.UUID;

record PublishedWorkSummaryResponse(
        UUID id,
        String title,
        WorkType type
) {

    static PublishedWorkSummaryResponse from(
            PublishedWorkSummary summary
    ) {
        return new PublishedWorkSummaryResponse(
                summary.id(),
                summary.title(),
                summary.type()
        );
    }
}