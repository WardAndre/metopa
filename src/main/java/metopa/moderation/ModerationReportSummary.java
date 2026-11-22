package metopa.moderation;

import java.time.Instant;
import java.util.UUID;

public record ModerationReportSummary(
        UUID id,
        UUID workId,
        ReportReason reason,
        String description,
        ReportStatus status,
        Instant createdAt
) {
}