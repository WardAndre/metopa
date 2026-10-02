package metopa.catalog;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreateWorkCommandTests {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldNormalizeWorkData() {
        CreateWorkCommand command = new CreateWorkCommand(
                UUID.randomUUID(),
                "  Metopa Origins  ",
                "  A graphic novel.  ",
                WorkType.GRAPHIC_NOVEL,
                ReadingDirection.LEFT_TO_RIGHT,
                PresentationMode.SINGLE_PAGE
        );

        assertThat(command.title())
                .isEqualTo("Metopa Origins");

        assertThat(command.description())
                .isEqualTo("A graphic novel.");
    }

    @Test
    void shouldRejectBlankTitle() {
        CreateWorkCommand command = new CreateWorkCommand(
                UUID.randomUUID(),
                "   ",
                "Description",
                WorkType.COMIC,
                ReadingDirection.LEFT_TO_RIGHT,
                PresentationMode.SINGLE_PAGE
        );

        Set<ConstraintViolation<CreateWorkCommand>> violations =
                validator.validate(command);

        assertThat(violations)
                .anyMatch(violation ->
                        violation.getPropertyPath()
                                .toString()
                                .equals("title"));
    }

    @Test
    void shouldConvertBlankDescriptionToNull() {
        CreateWorkCommand command = new CreateWorkCommand(
                UUID.randomUUID(),
                "Metopa",
                "   ",
                WorkType.COMIC,
                ReadingDirection.LEFT_TO_RIGHT,
                PresentationMode.SINGLE_PAGE
        );

        assertThat(command.description()).isNull();
    }
}