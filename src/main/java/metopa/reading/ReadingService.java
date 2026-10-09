package metopa.reading;

import metopa.publication.PublicationService;
import metopa.publication.PublishedPageReference;
import metopa.reading.internal.ReadingProgress;
import metopa.reading.internal.ReadingProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.Optional;

@Service
public class ReadingService {

    private final ReadingProgressRepository repository;
    private final PublicationService publicationService;

    public ReadingService(
            ReadingProgressRepository repository,
            PublicationService publicationService
    ) {
        this.repository = repository;
        this.publicationService = publicationService;
    }

    @Transactional
    public UUID saveProgress(
            UUID userId,
            UUID pageId
    ) {
        PublishedPageReference page =
                publicationService
                        .findPublishedPageReference(pageId);

        ReadingProgress progress =
                repository
                        .findByUserIdAndWorkId(
                                userId,
                                page.workId()
                        )
                        .map(existing -> {
                            existing.updatePosition(
                                    page.installmentId(),
                                    page.pageId()
                            );

                            return existing;
                        })
                        .orElseGet(() ->
                                new ReadingProgress(
                                        userId,
                                        page.workId(),
                                        page.installmentId(),
                                        page.pageId()
                                )
                        );

        ReadingProgress saved =
                repository.save(progress);

        return saved.getId();
    }

    @Transactional(readOnly = true)
    public Optional<ReadingProgressView> findProgress(
            UUID userId,
            UUID workId
    ) {
        return repository
                .findByUserIdAndWorkId(
                        userId,
                        workId
                )
                .map(progress ->
                        new ReadingProgressView(
                                progress.getId(),
                                progress.getWorkId(),
                                progress.getInstallmentId(),
                                progress.getPageId(),
                                progress.getUpdatedAt()
                        )
                );
    }
}