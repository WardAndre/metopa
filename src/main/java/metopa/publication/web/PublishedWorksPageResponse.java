package metopa.publication.web;

import java.util.List;

record PublishedWorksPageResponse(
        List<PublishedWorkSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}