package metopa.publication;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreateInstallmentCommandTests {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldNormalizeTitle() {
        CreateInstallmentCommand command =
                new CreateInstallmentCommand(
                        UUID.randomUUID(),
                        InstallmentType.CHAPTER,
                        1,
                        "  The Beginning  "
                );

        assertThat(command.title())
                .isEqualTo("The Beginning");
    }

    @Test
    void shouldConvertBlankTitleToNull() {
        CreateInstallmentCommand command =
                new CreateInstallmentCommand(
                        UUID.randomUUID(),
                        InstallmentType.CHAPTER,
                        1,
                        "   "
                );

        assertThat(command.title()).isNull();
    }

    @Test
    void shouldAllowNullTitle() {
        CreateInstallmentCommand command =
                new CreateInstallmentCommand(
                        UUID.randomUUID(),
                        InstallmentType.ISSUE,
                        1,
                        null
                );

        Set<ConstraintViolation<CreateInstallmentCommand>> violations =
                validator.validate(command);

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRejectNonPositiveNumber() {
        CreateInstallmentCommand command =
                new CreateInstallmentCommand(
                        UUID.randomUUID(),
                        InstallmentType.CHAPTER,
                        0,
                        null
                );

        Set<ConstraintViolation<CreateInstallmentCommand>> violations =
                validator.validate(command);

        assertThat(violations)
                .anyMatch(violation ->
                        violation.getPropertyPath()
                                .toString()
                                .equals("number"));
    }
}