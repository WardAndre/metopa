package metopa.publication;

import metopa.catalog.Genre;
import metopa.catalog.PresentationMode;
import metopa.catalog.ReadingDirection;
import metopa.catalog.WorkType;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PublishedWorkView(
        UUID id,
        String title,
        String description,
        WorkType type,
        ReadingDirection readingDirection,
        PresentationMode presentationMode,
        Set<Genre> genres,
        boolean hasCover,
        List<PublishedInstallmentSummary> installments
) {

    public PublishedWorkView {
        genres = Set.copyOf(genres);
        installments = List.copyOf(installments);
    }

    public PublishedWorkView(
            UUID id,
            String title,
            String description,
            WorkType type,
            ReadingDirection readingDirection,
            PresentationMode presentationMode,
            boolean hasCover,
            List<PublishedInstallmentSummary> installments
    ) {
        this(
                id,
                title,
                description,
                type,
                readingDirection,
                presentationMode,
                Set.of(),
                hasCover,
                installments
        );
    }
}