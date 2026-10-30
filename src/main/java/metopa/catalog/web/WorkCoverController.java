package metopa.catalog.web;

import metopa.catalog.CatalogService;
import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/works")
class WorkCoverController {

    private final CatalogService catalogService;
    private final IdentityService identityService;

    WorkCoverController(
            CatalogService catalogService,
            IdentityService identityService
    ) {
        this.catalogService = catalogService;
        this.identityService = identityService;
    }

    @PutMapping(
            value = "/{workId}/cover",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setCover(
            @PathVariable UUID workId,
            @RequestPart("file") MultipartFile file,
            Principal principal
    ) throws IOException {

        UserReference user =
                identityService.findByUsername(
                        principal.getName()
                );

        catalogService.setCover(
                user.id(),
                workId,
                file.getContentType(),
                file.getBytes()
        );
    }

    @DeleteMapping("/{workId}/cover")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeCover(
            @PathVariable UUID workId,
            Principal principal
    ) {
        var user = identityService.findByUsername(
                principal.getName()
        );

        catalogService.removeCover(
                user.id(),
                workId
        );
    }
}
