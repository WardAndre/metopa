package metopa.catalog.web;

import metopa.catalog.CatalogService;
import metopa.catalog.WorkSummary;
import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/me/works")
class CreatorWorkController {

    private final CatalogService catalogService;
    private final IdentityService identityService;

    CreatorWorkController(
            CatalogService catalogService,
            IdentityService identityService
    ) {
        this.catalogService = catalogService;
        this.identityService = identityService;
    }

    @GetMapping
    List<WorkSummary> findOwnWorks(
            Principal principal
    ) {
        UserReference user =
                identityService.findByUsername(
                        principal.getName()
                );

        return catalogService.findWorksByOwner(
                user.id()
        );
    }
}