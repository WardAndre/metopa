package metopa.moderation.web;

import metopa.catalog.WorkNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = WorkReportController.class)
public class ModerationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleInvalidReportData(
            MethodArgumentNotValidException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "One or more report fields are invalid."
        );

        problem.setTitle("Invalid report data");

        return problem;
    }

    @ExceptionHandler(WorkNotFoundException.class)
    public ProblemDetail handleWorkNotFound(
            WorkNotFoundException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "The requested work was not found."
        );

        problem.setTitle("Work not found");

        return problem;
    }
}