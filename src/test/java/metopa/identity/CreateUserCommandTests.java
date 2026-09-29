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
                "André"
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
                "André"
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
                ""
        );

        Set<ConstraintViolation<CreateUserCommand>> violations =
                validator.validate(command);

        assertThat(violations)
                .anyMatch(violation ->
                        violation.getPropertyPath().toString().equals("displayName"));
    }
}