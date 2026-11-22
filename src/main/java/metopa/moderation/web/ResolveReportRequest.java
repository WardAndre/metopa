package metopa.moderation.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import metopa.moderation.ModerationDecision;

public record ResolveReportRequest(
        @NotNull
        ModerationDecision decision,

        @NotBlank
        @Size(max = 2000)
        String note
) {
}