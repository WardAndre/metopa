package metopa.publication.web;

import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import metopa.publication.PublicationService;
import metopa.publication.UpdateInstallmentCommand;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/installments/{installmentId}")
class InstallmentPublicationController {

    private final PublicationService publicationService;
    private final IdentityService identityService;

    InstallmentPublicationController(
            PublicationService publicationService,
            IdentityService identityService
    ) {
        this.publicationService = publicationService;
        this.identityService = identityService;
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateInstallment(
            @PathVariable UUID installmentId,
            @RequestBody UpdateInstallmentRequest request,
            Principal principal
    ) {
        UserReference user =
                identityService.findByUsername(
                        principal.getName()
                );

        UpdateInstallmentCommand command =
                new UpdateInstallmentCommand(
                        request.type(),
                        request.number(),
                        request.title()
                );

        publicationService.updateInstallment(
                user.id(),
                installmentId,
                command
        );
    }

    @PostMapping("/publish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void publishInstallment(
            @PathVariable UUID installmentId,
            Principal principal
    ) {
        UserReference user =
                identityService.findByUsername(
                        principal.getName()
                );

        publicationService.publishInstallment(
                user.id(),
                installmentId
        );
    }
}