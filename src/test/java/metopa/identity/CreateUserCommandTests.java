package metopa.identity;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CreateUserCommandTests {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldRejectBlankUsername() {
        CreateUserCommand command = new CreateUserCommand(
                "",
                "andre@example.com",
                "André",
                "StrongPassword123!"
        );

        Set<ConstraintViolation<CreateUserCommand>> violations =
                validator.validate(command);

        assertThat(violations)
                .anyMatch(violation ->
                        violation.getPropertyPath().toString().equals("username"));
    }

    @Test
    void shouldRejectInvalidEmail() {
        CreateUserCommand command = new CreateUserCommand(
                "andre",
                "invalid-email",
                "André",
                "StrongPassword123!"
        );

        Set<ConstraintViolation<CreateUserCommand>> violations =
                validator.validate(command);

        assertThat(violations)
                .anyMatch(violation ->
                        violation.getPropertyPath().toString().equals("email"));
    }

    @Test
    void shouldRejectBlankDisplayName() {
        CreateUserCommand command = new CreateUserCommand(
                "andre",
                "andre@example.com",
                "",
                "StrongPassword123!"
        );

        Set<ConstraintViolation<CreateUserCommand>> violations =
                validator.validate(command);

        assertThat(violations)
                .anyMatch(violation ->
                        violation.getPropertyPath().toString().equals("displayName"));
    }

    @Test
    void shouldAcceptValidInputAfterNormalization() {
        CreateUserCommand command = new CreateUserCommand(
                "  Andre  ",
                "  Andre@Example.com  ",
                "  André Ward  ",
                "StrongPassword123!"
        );

        Set<ConstraintViolation<CreateUserCommand>> violations =
                validator.validate(command);

        assertThat(command.username()).isEqualTo("andre");
        assertThat(command.email()).isEqualTo("andre@example.com");
        assertThat(command.displayName()).isEqualTo("André Ward");

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRejectShortPassword() {
        CreateUserCommand command = new CreateUserCommand(
                "andre",
                "andre@example.com",
                "André",
                "short"
        );

        Set<ConstraintViolation<CreateUserCommand>> violations =
                validator.validate(command);

        assertThat(violations)
                .anyMatch(violation ->
                        violation.getPropertyPath()
                                .toString()
                                .equals("password"));
    }

    @Test
    void shouldNotNormalizePassword() {
        String password = "  Strong Password 123!  ";

        CreateUserCommand command = new CreateUserCommand(
                "  Andre  ",
                "  Andre@Example.com  ",
                "  André Ward  ",
                password
        );

        assertThat(command.username()).isEqualTo("andre");
        assertThat(command.email()).isEqualTo("andre@example.com");
        assertThat(command.displayName()).isEqualTo("André Ward");

        assertThat(command.password()).isEqualTo(password);
    }
}