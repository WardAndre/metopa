package metopa.publication.web;

import metopa.identity.IdentityService;
import metopa.identity.UserReference;
import metopa.publication.CreatorInstallmentSummary;
import metopa.publication.CreatorInstallmentView;
import metopa.publication.PublicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/me")
class CreatorInstallmentController {

    private final PublicationService publicationService;
    private final IdentityService identityService;

    CreatorInstallmentController(
            PublicationService publicationService,
            IdentityService identityService
    ) {
        this.publicationService = publicationService;
        this.identityService = identityService;
    }

    @GetMapping("/works/{workId}/installments")
    List<CreatorInstallmentSummary> findInstallments(
            @PathVariable UUID workId,
            Principal principal
    ) {
        UserReference user =
                identityService.findByUsername(
                        principal.getName()
                );

        return publicationService
                .findCreatorInstallments(
                        user.id(),
                        workId
                );
    }

    @GetMapping("/installments/{installmentId}")
    CreatorInstallmentView findInstallment(
            @PathVariable UUID installmentId,
            Principal principal
    ) {
        UserReference user =
                identityService.findByUsername(
                        principal.getName()
                );

        return publicationService
                .findCreatorInstallment(
                        user.id(),
                        installmentId
                );
    }
}