package metopa.publication.web;

import metopa.publication.InstallmentType;
import metopa.publication.PublishedInstallmentView;

import java.util.List;
import java.util.UUID;

record PublishedInstallmentResponse(
        UUID id,
        UUID workId,
        InstallmentType type,
        int number,
        String title,
        List<PageResponse> pages
) {

    static PublishedInstallmentResponse from(
            PublishedInstallmentView view
    ) {
        return new PublishedInstallmentResponse(
                view.id(),
                view.workId(),
                view.type(),
                view.number(),
                view.title(),
                view.pages()
                        .stream()
                        .map(page ->
                                new PageResponse(
                                        page.id(),
                                        page.number(),
                                        page.contentType()
                                )
                        )
                        .toList()
        );
    }

    record PageResponse(
            UUID id,
            int number,
            String contentType
    ) {
    }
}