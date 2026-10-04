package metopa.publication.web;

import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import metopa.publication.CreateInstallmentCommand;
import metopa.publication.PublicationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/works/{workId}/installments")
class InstallmentController {

    private final PublicationService publicationService;
    private final IdentityService identityService;

    InstallmentController(
            PublicationService publicationService,
            IdentityService identityService
    ) {
        this.publicationService = publicationService;
        this.identityService = identityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreateInstallmentResponse createInstallment(
            @PathVariable UUID workId,
            @RequestBody CreateInstallmentRequest request,
            Principal principal
    ) {
        UserReference user =
                identityService.findByUsername(principal.getName());

        CreateInstallmentCommand command =
                new CreateInstallmentCommand(
                        workId,
                        request.type(),
                        request.number(),
                        request.title()
                );

        UUID installmentId =
                publicationService.createInstallment(
                        user.id(),
                        command
                );

        return new CreateInstallmentResponse(installmentId);
    }
}