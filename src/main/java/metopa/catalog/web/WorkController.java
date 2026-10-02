package metopa.catalog.web;

import metopa.catalog.CatalogService;
import metopa.catalog.CreateWorkCommand;
import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import org.springframework.http.HttpStatus;
import java.security.Principal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/works")
class WorkController {

    private final CatalogService catalogService;
    private final IdentityService identityService;

    WorkController(
            CatalogService catalogService,
            IdentityService identityService
    ) {
        this.catalogService = catalogService;
        this.identityService = identityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreateWorkResponse createWork(
            @RequestBody CreateWorkRequest request,
            Principal principal
    ) {
        UserReference user =
                identityService.findByUsername(principal.getName());

        CreateWorkCommand command = new CreateWorkCommand(
                user.id(),
                request.title(),
                request.description(),
                request.type(),
                request.readingDirection(),
                request.presentationMode()
        );

        UUID workId = catalogService.createWork(command);

        return new CreateWorkResponse(workId);
    }
}