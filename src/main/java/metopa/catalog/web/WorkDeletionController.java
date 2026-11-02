package metopa.catalog.web;

import metopa.catalog.CatalogService;
import metopa.identity.IdentityService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/works")
class WorkDeletionController {

    private final CatalogService catalogService;
    private final IdentityService identityService;

    WorkDeletionController(
            CatalogService catalogService,
            IdentityService identityService
    ) {
        this.catalogService = catalogService;
        this.identityService = identityService;
    }

    @DeleteMapping("/{workId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWork(
            @PathVariable UUID workId,
            Principal principal
    ) {
        var user = identityService.findByUsername(
                principal.getName()
        );

        catalogService.deleteWork(
                user.id(),
                workId
        );
    }
}
