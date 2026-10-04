package metopa.publication.web;

import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import metopa.publication.CreatePageCommand;
import metopa.publication.PublicationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/installments/{installmentId}/pages")
class PageController {

    private final PublicationService publicationService;
    private final IdentityService identityService;

    PageController(
            PublicationService publicationService,
            IdentityService identityService
    ) {
        this.publicationService = publicationService;
        this.identityService = identityService;
    }

    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    CreatePageResponse createPage(
            @PathVariable UUID installmentId,
            @RequestParam int number,
            @RequestPart("file") MultipartFile file,
            Principal principal
    ) throws IOException {

        UserReference user =
                identityService.findByUsername(
                        principal.getName()
                );

        CreatePageCommand command =
                new CreatePageCommand(
                        installmentId,
                        number,
                        file.getContentType(),
                        file.getBytes()
                );

        UUID pageId =
                publicationService.createPage(
                        user.id(),
                        command
                );

        return new CreatePageResponse(pageId);
    }
}