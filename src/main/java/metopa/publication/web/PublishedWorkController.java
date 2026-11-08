package metopa.publication.web;

import metopa.catalog.Genre;
import metopa.publication.PublicationService;
import metopa.publication.PublishedWorkSummary;
import metopa.publication.PublishedWorkView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/works")
class PublishedWorkController {

    private final PublicationService publicationService;

    PublishedWorkController(
            PublicationService publicationService
    ) {
        this.publicationService = publicationService;
    }

    @GetMapping("/{workId}")
    PublishedWorkResponse findPublishedWork(
            @PathVariable UUID workId
    ) {
        PublishedWorkView work =
                publicationService.findPublishedWork(
                        workId
                );

        return PublishedWorkResponse.from(work);
    }

    @GetMapping
    PublishedWorksPageResponse findPublishedWorks(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Genre genre,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid pagination parameters."
            );
        }

        Page<PublishedWorkSummary> works =
                publicationService.findPublishedWorks(
                        title,
                        genre,
                        PageRequest.of(page, size)
                );

        Page<PublishedWorkSummaryResponse> response =
                works.map(PublishedWorkSummaryResponse::from);

        return new PublishedWorksPageResponse(
                response.getContent(),
                response.getNumber(),
                response.getSize(),
                response.getTotalElements(),
                response.getTotalPages()
        );
    }
}