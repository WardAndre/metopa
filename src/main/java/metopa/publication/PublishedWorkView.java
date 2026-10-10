package metopa.publication;

import metopa.catalog.PresentationMode;
import metopa.catalog.ReadingDirection;
import metopa.catalog.WorkType;

import java.util.List;
import java.util.UUID;

public record PublishedWorkView(
        UUID id,
        String title,
        String description,
        WorkType type,
        ReadingDirection readingDirection,
        PresentationMode presentationMode,
        List<PublishedInstallmentSummary> installments
) {

    public PublishedWorkView {
        installments = List.copyOf(installments);
    }
}