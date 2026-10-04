package metopa.publication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Locale;
import java.util.UUID;

public record CreatePageCommand(

        @NotNull
        UUID installmentId,

        @Positive
        int number,

        @NotBlank
        @Size(max = 100)
        String contentType,

        @NotEmpty
        byte[] content
) {

    public CreatePageCommand {
        contentType = normalizeContentType(contentType);
    }

    private static String normalizeContentType(String value) {
        return value == null
                ? null
                : value.trim().toLowerCase(Locale.ROOT);
    }
}