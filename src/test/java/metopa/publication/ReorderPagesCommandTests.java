package metopa.publication;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReorderPagesCommandTests {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory()
                    .getValidator();

    @Test
    void shouldAcceptValidPageOrder() {
        UUID firstPageId = UUID.randomUUID();
        UUID secondPageId = UUID.randomUUID();

        ReorderPagesCommand command =
                new ReorderPagesCommand(
                        List.of(secondPageId, firstPageId)
                );

        Set<ConstraintViolation<ReorderPagesCommand>> violations =
                validator.validate(command);

        assertThat(violations).isEmpty();

        assertThat(command.pageIds())
                .containsExactly(
                        secondPageId,
                        firstPageId
                );
    }

    @Test
    void shouldRejectEmptyPageOrder() {
        ReorderPagesCommand command =
                new ReorderPagesCommand(List.of());

        Set<ConstraintViolation<ReorderPagesCommand>> violations =
                validator.validate(command);

        assertThat(violations)
                .anyMatch(violation ->
                        violation.getPropertyPath()
                                .toString()
                                .equals("pageIds")
                );
    }

    @Test
    void shouldRejectNullPageOrder() {
        ReorderPagesCommand command =
                new ReorderPagesCommand(null);

        Set<ConstraintViolation<ReorderPagesCommand>> violations =
                validator.validate(command);

        assertThat(violations)
                .anyMatch(violation ->
                        violation.getPropertyPath()
                                .toString()
                                .equals("pageIds")
                );
    }

    @Test
    void shouldRejectNullPageId() {
        ReorderPagesCommand command =
                new ReorderPagesCommand(
                        Arrays.asList(
                                UUID.randomUUID(),
                                null
                        )
                );

        Set<ConstraintViolation<ReorderPagesCommand>> violations =
                validator.validate(command);

        assertThat(violations)
                .anyMatch(violation ->
                        violation.getPropertyPath()
                                .toString()
                                .startsWith("pageIds")
                );
    }
}