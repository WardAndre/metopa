package metopa.publication.web;

import jakarta.validation.ConstraintViolationException;
import metopa.catalog.WorkNotFoundException;
import metopa.publication.InstallmentAlreadyExistsException;
import metopa.publication.InstallmentNotFoundException;
import metopa.publication.PageAlreadyExistsException;
import metopa.publication.WorkAccessDeniedException;
import metopa.publication.InstallmentHasNoPagesException;
import metopa.publication.PublishedInstallmentModificationException;
import metopa.publication.PublishedInstallmentNotFoundException;
import metopa.publication.PublishedPageNotFoundException;
import metopa.publication.PublishedWorkNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
        basePackages = "metopa.publication.web"
)
class PublicationExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatus(
                        HttpStatus.BAD_REQUEST
                );

        problem.setTitle("Invalid publication data");
        problem.setDetail(
                "One or more publication fields are invalid."
        );

        return problem;
    }

    @ExceptionHandler(WorkNotFoundException.class)
    ProblemDetail handleWorkNotFound(
            WorkNotFoundException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatus(
                        HttpStatus.NOT_FOUND
                );

        problem.setTitle("Work not found");
        problem.setDetail(
                "The requested work could not be found."
        );

        return problem;
    }

    @ExceptionHandler(InstallmentNotFoundException.class)
    ProblemDetail handleInstallmentNotFound(
            InstallmentNotFoundException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatus(
                        HttpStatus.NOT_FOUND
                );

        problem.setTitle("Installment not found");
        problem.setDetail(
                "The requested installment could not be found."
        );

        return problem;
    }

    @ExceptionHandler(WorkAccessDeniedException.class)
    ProblemDetail handleWorkAccessDenied(
            WorkAccessDeniedException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatus(
                        HttpStatus.FORBIDDEN
                );

        problem.setTitle("Access denied");
        problem.setDetail(
                "You do not have permission to modify this work."
        );

        return problem;
    }

    @ExceptionHandler(InstallmentAlreadyExistsException.class)
    ProblemDetail handleInstallmentAlreadyExists(
            InstallmentAlreadyExistsException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatus(
                        HttpStatus.CONFLICT
                );

        problem.setTitle("Installment already exists");
        problem.setDetail(
                "An installment with this type and number already exists for the work."
        );

        return problem;
    }

    @ExceptionHandler(PageAlreadyExistsException.class)
    ProblemDetail handlePageAlreadyExists(
            PageAlreadyExistsException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatus(
                        HttpStatus.CONFLICT
                );

        problem.setTitle("Page already exists");
        problem.setDetail(
                "A page with this number already exists for the installment."
        );

        return problem;
    }

    @ExceptionHandler(InstallmentHasNoPagesException.class)
    ProblemDetail handleInstallmentHasNoPages(
            InstallmentHasNoPagesException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatus(
                        HttpStatus.CONFLICT
                );

        problem.setTitle(
                "Installment cannot be published"
        );
        problem.setDetail(
                "An installment must contain at least one page before publication."
        );

        return problem;
    }

    @ExceptionHandler(
            PublishedInstallmentModificationException.class
    )
    ProblemDetail handlePublishedInstallmentModification(
            PublishedInstallmentModificationException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatus(
                        HttpStatus.CONFLICT
                );

        problem.setTitle(
                "Published installment cannot be modified"
        );
        problem.setDetail(
                "Published installments cannot be modified."
        );

        return problem;
    }

    @ExceptionHandler(
            PublishedInstallmentNotFoundException.class
    )
    ProblemDetail handlePublishedInstallmentNotFound(
            PublishedInstallmentNotFoundException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatus(
                        HttpStatus.NOT_FOUND
                );

        problem.setTitle(
                "Published installment not found"
        );
        problem.setDetail(
                "The requested published installment could not be found."
        );

        return problem;
    }

    @ExceptionHandler(
            PublishedPageNotFoundException.class
    )
    ProblemDetail handlePublishedPageNotFound(
            PublishedPageNotFoundException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatus(
                        HttpStatus.NOT_FOUND
                );

        problem.setTitle(
                "Published page not found"
        );
        problem.setDetail(
                "The requested published page could not be found."
        );

        return problem;
    }

    @ExceptionHandler(
            PublishedWorkNotFoundException.class
    )
    ProblemDetail handlePublishedWorkNotFound(
            PublishedWorkNotFoundException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatus(
                        HttpStatus.NOT_FOUND
                );

        problem.setTitle(
                "Work not available"
        );

        problem.setDetail(
                "The requested work is not available."
        );

        return problem;
    }
}