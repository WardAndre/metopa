package metopa.publication.web;

import metopa.publication.PublicationService;
import metopa.publication.PublishedWorkCoverContent;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/works")
class PublishedWorkCoverController {

    private final PublicationService publicationService;

    PublishedWorkCoverController(
            PublicationService publicationService
    ) {
        this.publicationService = publicationService;
    }

    @GetMapping("/{workId}/cover")
    ResponseEntity<byte[]> findPublishedWorkCover(
            @PathVariable UUID workId
    ) {
        PublishedWorkCoverContent cover =
                publicationService
                        .findPublishedWorkCover(
                                workId
                        );

        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                cover.contentType()
                        )
                )
                .body(
                        cover.content()
                );
    }
}