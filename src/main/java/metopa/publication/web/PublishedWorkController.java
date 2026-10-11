package metopa.publication.web;

import metopa.publication.PublicationService;
import metopa.publication.PublishedWorkView;
import metopa.publication.PublishedWorkSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    List<PublishedWorkSummaryResponse> findPublishedWorks() {
        return publicationService
                .findPublishedWorks()
                .stream()
                .map(PublishedWorkSummaryResponse::from)
                .toList();
    }
}