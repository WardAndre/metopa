package metopa.publication.web;

import metopa.publication.PublicationService;
import metopa.publication.PublishedPageContent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/pages")
class PublishedPageController {

    private final PublicationService publicationService;

    PublishedPageController(
            PublicationService publicationService
    ) {
        this.publicationService = publicationService;
    }

    @GetMapping("/{pageId}/content")
    ResponseEntity<byte[]> findPublishedPageContent(
            @PathVariable UUID pageId
    ) {
        PublishedPageContent page =
                publicationService
                        .findPublishedPageContent(pageId);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_TYPE,
                        page.contentType()
                )
                .body(page.content());
    }
}