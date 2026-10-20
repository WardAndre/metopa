package metopa.publication.web;

import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import metopa.publication.CreatorPageContent;
import metopa.publication.PublicationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/me/pages")
class CreatorPageController {

    private final PublicationService publicationService;
    private final IdentityService identityService;

    CreatorPageController(
            PublicationService publicationService,
            IdentityService identityService
    ) {
        this.publicationService = publicationService;
        this.identityService = identityService;
    }

    @GetMapping("/{pageId}/content")
    ResponseEntity<byte[]> findCreatorPageContent(
            @PathVariable UUID pageId,
            Principal principal
    ) {
        UserReference user =
                identityService.findByUsername(
                        principal.getName()
                );

        CreatorPageContent page =
                publicationService
                        .findCreatorPageContent(
                                user.id(),
                                pageId
                        );

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_TYPE,
                        page.contentType()
                )
                .body(page.content());
    }
}