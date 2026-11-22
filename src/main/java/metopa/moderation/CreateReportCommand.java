package metopa.moderation;

import java.util.UUID;

public record CreateReportCommand(
        UUID reporterUserId,
        UUID workId,
        ReportReason reason,
        String description
) {
}