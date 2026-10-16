package metopa.publication.web;

import metopa.publication.PublicationService;
import metopa.publication.PublishedWorkView;
import metopa.publication.PublishedWorkSummary;
import metopa.catalog.Genre;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;
import java.util.List;

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
    List<PublishedWorkSummaryResponse> findPublishedWorks(
            @RequestParam(required = false)
            Genre genre
    ) {
        List<PublishedWorkSummary> works =
                genre == null
                        ? publicationService.findPublishedWorks()
                        : publicationService.findPublishedWorks(
                        genre
                );

        return works
                .stream()
                .map(PublishedWorkSummaryResponse::from)
                .toList();
    }
}