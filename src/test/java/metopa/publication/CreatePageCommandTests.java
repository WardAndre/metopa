package metopa.publication;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreatePageCommandTests {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldNormalizePageData() {
        CreatePageCommand command =
                new CreatePageCommand(
                        UUID.randomUUID(),
                        1,
                        "  IMAGE/JPEG  ",
                        "page-content".getBytes()
                );

        assertThat(command.contentType())
                .isEqualTo("image/jpeg");

        assertThat(command.content())
                .isNotEmpty();
    }

    @Test
    void shouldRejectNonPositiveNumber() {
        CreatePageCommand command =
                new CreatePageCommand(
                        UUID.randomUUID(),
                        0,
                        "image/jpeg",
                        "page-content".getBytes()
                );

        Set<ConstraintViolation<CreatePageCommand>> violations =
                validator.validate(command);

        assertThat(violations)
                .anyMatch(violation ->
                        violation.getPropertyPath()
                                .toString()
                                .equals("number"));
    }

    @Test
    void shouldRejectBlankContentType() {
        CreatePageCommand command =
                new CreatePageCommand(
                        UUID.randomUUID(),
                        1,
                        "   ",
                        "page-content".getBytes()
                );

        Set<ConstraintViolation<CreatePageCommand>> violations =
                validator.validate(command);

        assertThat(violations)
                .anyMatch(violation ->
                        violation.getPropertyPath()
                                .toString()
                                .equals("contentType"));
    }

    @Test
    void shouldRejectEmptyContent() {
        CreatePageCommand command =
                new CreatePageCommand(
                        UUID.randomUUID(),
                        1,
                        "image/jpeg",
                        new byte[0]
                );

        Set<ConstraintViolation<CreatePageCommand>> violations =
                validator.validate(command);

        assertThat(violations)
                .anyMatch(violation ->
                        violation.getPropertyPath()
                                .toString()
                                .equals("content"));
    }
}