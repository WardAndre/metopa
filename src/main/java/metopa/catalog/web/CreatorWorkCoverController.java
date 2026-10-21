package metopa.catalog.web;

import metopa.catalog.CatalogService;
import metopa.catalog.WorkCoverContent;
import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/me/works")
class CreatorWorkCoverController {

    private final CatalogService catalogService;
    private final IdentityService identityService;

    CreatorWorkCoverController(
            CatalogService catalogService,
            IdentityService identityService
    ) {
        this.catalogService = catalogService;
        this.identityService = identityService;
    }

    @GetMapping("/{workId}/cover")
    ResponseEntity<byte[]> findCreatorWorkCover(
            @PathVariable UUID workId,
            Principal principal
    ) {
        UserReference user =
                identityService.findByUsername(
                        principal.getName()
                );

        WorkCoverContent cover =
                catalogService.findCreatorWorkCover(
                        user.id(),
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