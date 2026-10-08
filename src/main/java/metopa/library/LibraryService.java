package metopa.library;

import metopa.catalog.CatalogService;
import metopa.catalog.WorkSummary;
import metopa.library.internal.LibraryEntry;
import metopa.library.internal.LibraryEntryRepository;
import metopa.publication.PublicationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LibraryService {

    private final LibraryEntryRepository repository;
    private final PublicationService publicationService;
    private final CatalogService catalogService;

    public LibraryService(
            LibraryEntryRepository repository,
            PublicationService publicationService,
            CatalogService catalogService
    ) {
        this.repository = repository;
        this.publicationService = publicationService;
        this.catalogService = catalogService;
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

    @Transactional(readOnly = true)
    public List<LibraryEntryView> findLibrary(
            UUID userId
    ) {
        return repository
                .findByUserIdOrderByAddedAtDesc(userId)
                .stream()
                .map(entry -> {
                    WorkSummary work =
                            catalogService.findWorkSummary(
                                    entry.getWorkId()
                            );

                    return new LibraryEntryView(
                            entry.getId(),
                            work.id(),
                            work.title(),
                            work.type(),
                            entry.getAddedAt()
                    );
                })
                .toList();
    }

    @Transactional
    public void removeWork(
            UUID userId,
            UUID workId
    ) {
        repository.deleteByUserIdAndWorkId(
                userId,
                workId
        );
    }
}