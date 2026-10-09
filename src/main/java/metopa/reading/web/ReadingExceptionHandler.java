package metopa.reading.web;

import metopa.publication.PublishedPageNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
        basePackages = "metopa.reading.web"
)
class ReadingExceptionHandler {

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
                "Page not available"
        );

        problem.setDetail(
                "The requested page is not available for reading progress."
        );

        return problem;
    }
}