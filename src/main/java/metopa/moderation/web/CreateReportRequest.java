package metopa.moderation.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import metopa.moderation.ReportReason;

public record CreateReportRequest(
        @NotNull ReportReason reason,

        @NotBlank
        @Size(max = 2000)
        String description
) {
}