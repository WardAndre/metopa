package metopa.publication;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateInstallmentCommand(

        @NotNull
        UUID workId,

        @NotNull
        InstallmentType type,

        @Positive
        int number,

        @Size(max = 200)
        String title
) {

    public CreateInstallmentCommand {
        title = normalizeNullable(title);
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