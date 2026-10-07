package metopa.library;

import metopa.library.internal.LibraryEntry;
import metopa.library.internal.LibraryEntryRepository;
import metopa.publication.PublicationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class LibraryService {

    private final LibraryEntryRepository repository;
    private final PublicationService publicationService;

    public LibraryService(
            LibraryEntryRepository repository,
            PublicationService publicationService
    ) {
        this.repository = repository;
        this.publicationService = publicationService;
    }

    @Transactional
    public UUID addWork(
            UUID userId,
            UUID workId
    ) {
        if (!publicationService.hasPublishedContent(workId)) {
            throw new WorkNotAvailableForLibraryException();
        }

        if (repository.existsByUserIdAndWorkId(
                userId,
                workId
        )) {
            throw new LibraryEntryAlreadyExistsException();
        }

        LibraryEntry entry =
                new LibraryEntry(
                        userId,
                        workId
                );

        LibraryEntry saved =
                repository.save(entry);

        return saved.getId();
    }
}