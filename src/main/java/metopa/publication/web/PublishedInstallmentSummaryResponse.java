package metopa.publication.web;

import metopa.publication.InstallmentType;
import metopa.publication.PublishedInstallmentSummary;

import java.util.UUID;

record PublishedInstallmentSummaryResponse(
        UUID id,
        InstallmentType type,
        int number,
        String title
) {

    static PublishedInstallmentSummaryResponse from(
            PublishedInstallmentSummary summary
    ) {
        return new PublishedInstallmentSummaryResponse(
                summary.id(),
                summary.type(),
                summary.number(),
                summary.title()
        );
    }
}