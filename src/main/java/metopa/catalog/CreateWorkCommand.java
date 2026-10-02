package metopa.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateWorkCommand(

        @NotNull
        UUID ownerId,

        @NotBlank
        @Size(max = 200)
        String title,

        @Size(max = 5000)
        String description,

        @NotNull
        WorkType type,

        @NotNull
        ReadingDirection readingDirection,

        @NotNull
        PresentationMode presentationMode
) {

    public CreateWorkCommand {
        title = normalize(title);
        description = normalizeNullable(description);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }
}