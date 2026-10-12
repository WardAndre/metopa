package metopa.catalog.web;

import metopa.catalog.WorkAccessDeniedException;

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

    @ExceptionHandler(
            WorkAccessDeniedException.class
    )
    ProblemDetail handleWorkAccessDenied(
            WorkAccessDeniedException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatus(
                        HttpStatus.FORBIDDEN
                );

        problem.setTitle(
                "Work access denied"
        );

        problem.setDetail(
                "The authenticated user cannot modify this work."
        );

        return problem;
    }
}