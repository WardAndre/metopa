package metopa.publication;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateInstallmentCommand(

        @NotNull
        InstallmentType type,

        @Positive
        int number,

        @Size(max = 200)
        String title
) {

    public UpdateInstallmentCommand {
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