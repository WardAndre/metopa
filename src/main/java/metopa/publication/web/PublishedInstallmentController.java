package metopa.publication.web;

import metopa.publication.PublicationService;
import metopa.publication.PublishedInstallmentView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/installments")
class PublishedInstallmentController {

    private final PublicationService publicationService;

    PublishedInstallmentController(
            PublicationService publicationService
    ) {
        this.publicationService = publicationService;
    }

    @GetMapping("/{installmentId}")
    PublishedInstallmentResponse findPublishedInstallment(
            @PathVariable UUID installmentId
    ) {
        PublishedInstallmentView view =
                publicationService
                        .findPublishedInstallment(
                                installmentId
                        );

        return PublishedInstallmentResponse.from(
                view
        );
    }
}