package metopa.identity.web;

import jakarta.validation.ConstraintViolationException;
import metopa.identity.EmailAlreadyExistsException;
import metopa.identity.UsernameAlreadyExistsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "metopa.identity.web")
public class IdentityExceptionHandler {

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ProblemDetail handleUsernameAlreadyExists(
            UsernameAlreadyExistsException exception
    ) {
        return accountConflict();
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ProblemDetail handleEmailAlreadyExists(
            EmailAlreadyExistsException exception
    ) {
        return accountConflict();
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleValidationError(
            ConstraintViolationException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

        problem.setTitle("Invalid account data");
        problem.setDetail(
                "One or more account fields are invalid."
        );

        return problem;
    }

    private ProblemDetail accountConflict() {
        ProblemDetail problem =
                ProblemDetail.forStatus(HttpStatus.CONFLICT);

        problem.setTitle("Account data already in use");
        problem.setDetail(
                "An account cannot be created with the provided credentials."
        );

        return problem;
    }
}