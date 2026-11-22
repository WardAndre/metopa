package metopa.moderation.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RestoreWorkRequest(
        @NotBlank
        @Size(max = 2000)
        String note
) {
}