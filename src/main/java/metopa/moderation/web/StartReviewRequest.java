package metopa.moderation.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StartReviewRequest(
        @NotBlank
        @Size(max = 2000)
        String note
) {
}