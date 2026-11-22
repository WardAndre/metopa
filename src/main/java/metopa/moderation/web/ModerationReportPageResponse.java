package metopa.moderation.web;

import metopa.moderation.ModerationReportSummary;
import org.springframework.data.domain.Page;

import java.util.List;

public record ModerationReportPageResponse(
        List<ModerationReportSummary> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static ModerationReportPageResponse from(
            Page<ModerationReportSummary> reports
    ) {
        return new ModerationReportPageResponse(
                reports.getContent(),
                reports.getNumber(),
                reports.getSize(),
                reports.getTotalElements(),
                reports.getTotalPages()
        );
    }
}
