package metopa.library.web;

import metopa.library.LibraryEntryAlreadyExistsException;
import metopa.library.WorkNotAvailableForLibraryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
        basePackages = "metopa.library.web"
)
class LibraryExceptionHandler {

    @ExceptionHandler(
            WorkNotAvailableForLibraryException.class
    )
    ProblemDetail handleWorkNotAvailable(
            WorkNotAvailableForLibraryException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatus(
                        HttpStatus.NOT_FOUND
                );

        problem.setTitle(
                "Work not available"
        );

        problem.setDetail(
                "The requested work is not available for the library."
        );

        return problem;
    }

    @ExceptionHandler(
            LibraryEntryAlreadyExistsException.class
    )
    ProblemDetail handleLibraryEntryAlreadyExists(
            LibraryEntryAlreadyExistsException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatus(
                        HttpStatus.CONFLICT
                );

        problem.setTitle(
                "Work already in library"
        );

        problem.setDetail(
                "The work is already present in the user's library."
        );

        return problem;
    }
}