package metopa.catalog.web;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "metopa.catalog.web")
class CatalogExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(
                HttpStatus.BAD_REQUEST
        );

        problem.setTitle("Invalid work data");
        problem.setDetail(
                "One or more work fields are invalid."
        );

        return problem;
    }
}