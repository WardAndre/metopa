package metopa.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record CreateUserCommand(

        @NotBlank
        @Size(min = 3, max = 50)
        String username,

        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotBlank
        @Size(max = 100)
        String displayName,

        @NotBlank
        @Size(min = 12, max = 128)
        String password
) {

    public CreateUserCommand {
        username = normalizeToLowerCase(username);
        email = normalizeToLowerCase(email);
        displayName = normalize(displayName);
    }

    private static String normalizeToLowerCase(String value) {
        return value == null
                ? null
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return value == null
                ? null
                : value.trim();
    }
}