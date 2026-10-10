package metopa.publication.web;

import metopa.catalog.PresentationMode;
import metopa.catalog.ReadingDirection;
import metopa.catalog.WorkType;
import metopa.publication.PublishedWorkView;

import java.util.List;
import java.util.UUID;

record PublishedWorkResponse(
        UUID id,
        String title,
        String description,
        WorkType type,
        ReadingDirection readingDirection,
        PresentationMode presentationMode,
        List<PublishedInstallmentSummaryResponse> installments
) {

    static PublishedWorkResponse from(
            PublishedWorkView view
    ) {
        return new PublishedWorkResponse(
                view.id(),
                view.title(),
                view.description(),
                view.type(),
                view.readingDirection(),
                view.presentationMode(),
                view.installments()
                        .stream()
                        .map(
                                PublishedInstallmentSummaryResponse::from
                        )
                        .toList()
        );
    }
}