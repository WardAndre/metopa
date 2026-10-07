package metopa.library.web;

import metopa.catalog.WorkType;
import metopa.library.LibraryEntryView;

import java.time.Instant;
import java.util.UUID;

record LibraryEntryResponse(
        UUID id,
        UUID workId,
        String title,
        WorkType type,
        Instant addedAt
) {

    static LibraryEntryResponse from(
            LibraryEntryView view
    ) {
        return new LibraryEntryResponse(
                view.id(),
                view.workId(),
                view.title(),
                view.type(),
                view.addedAt()
        );
    }
}