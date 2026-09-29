package metopa.identity;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class IdentityServiceValidationTests {

    @Autowired
    private IdentityService identityService;

    @Test
    void shouldRejectInvalidCommandAtModuleBoundary() {
        CreateUserCommand command = new CreateUserCommand(
                "",
                "invalid-email",
                ""
        );

        assertThatThrownBy(() -> identityService.createUser(command))
                .isInstanceOf(ConstraintViolationException.class);
    }
}