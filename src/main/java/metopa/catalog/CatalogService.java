package metopa.catalog;

import jakarta.validation.Valid;
import metopa.catalog.internal.Work;
import metopa.catalog.internal.WorkRepository;
import metopa.media.BinaryStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;

@Service
@Validated
public class CatalogService {

    private final WorkRepository repository;

    private final BinaryStorage binaryStorage;

    public CatalogService(
            WorkRepository repository,
            BinaryStorage binaryStorage
    ) {
        this.repository = repository;
        this.binaryStorage = binaryStorage;
    }

    @Transactional
    public UUID createWork(@Valid CreateWorkCommand command) {
        Work work = new Work(
                command.ownerId(),
                command.title(),
                command.description(),
                command.type(),
                command.readingDirection(),
                command.presentationMode()
        );

        work.setGenres(
                command.genres()
        );

        Work saved = repository.save(work);

        return saved.getId();
    }

    @Transactional
    public void updateWork(
            UUID userId,
            UUID workId,
            @Valid UpdateWorkCommand command
    ) {
        Work work = repository.findByIdForUpdate(workId)
                .orElseThrow(() ->
                        new WorkNotFoundException(workId)
                );

        if (!work.getOwnerId().equals(userId)) {
            throw new WorkAccessDeniedException();
        }

        work.updateMetadata(
                command.title(),
                command.description(),
                command.type(),
                command.readingDirection(),
                command.presentationMode(),
                command.genres()
        );
    }

    @Transactional(readOnly = true)
    public WorkReference findWork(UUID workId) {
        Work work = repository.findById(workId)
                .orElseThrow(() ->
                        new WorkNotFoundException(workId)
                );

        return new WorkReference(
                work.getId(),
                work.getOwnerId()
        );
    }

    @Transactional(readOnly = true)
    public WorkSummary findWorkSummary(UUID workId) {
        Work work = repository.findById(workId)
                .orElseThrow(() ->
                        new WorkNotFoundException(workId)
                );

        return new WorkSummary(
                work.getId(),
                work.getTitle(),
                work.getType(),
                work.getGenres(),
                work.getCoverStorageKey() != null
        );
    }

    @Transactional(readOnly = true)
    public WorkDetails findWorkDetails(UUID workId) {
        Work work = repository.findById(workId)
                .orElseThrow(() ->
                        new WorkNotFoundException(workId)
                );

        return new WorkDetails(
                work.getId(),
                work.getTitle(),
                work.getDescription(),
                work.getType(),
                work.getReadingDirection(),
                work.getPresentationMode(),
                work.getGenres(),
                work.getCoverStorageKey() != null
        );
    }

    @Transactional
    public void setCover(
            UUID userId,
            UUID workId,
            String contentType,
            byte[] content
    ) {
        Work work = repository.findByIdForUpdate(workId)
                .orElseThrow(() ->
                        new WorkNotFoundException(workId)
                );

        if (!work.getOwnerId().equals(userId)) {
            throw new WorkAccessDeniedException();
        }

        String previousStorageKey =
                work.getCoverStorageKey();

        String storageKey =
                binaryStorage.store(
                        "covers",
                        workId,
                        content
                );

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        if (previousStorageKey != null
                                && !previousStorageKey.equals(storageKey)) {
                            binaryStorage.delete(previousStorageKey);
                        }
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            binaryStorage.delete(storageKey);
                        }
                    }
                }
        );

        work.setCover(
                storageKey,
                contentType
        );
    }

    @Transactional(readOnly = true)
    public WorkCoverContent findWorkCover(
            UUID workId
    ) {
        Work work = repository.findById(workId)
                .orElseThrow(() ->
                        new WorkNotFoundException(workId)
                );

        if (work.getCoverStorageKey() == null) {
            throw new WorkCoverNotFoundException(
                    workId
            );
        }

        byte[] content =
                binaryStorage.read(
                        work.getCoverStorageKey()
                );

        return new WorkCoverContent(
                work.getCoverContentType(),
                content
        );
    }

    @Transactional(readOnly = true)
    public List<WorkSummary> findWorksByOwner(
            UUID ownerId
    ) {
        return repository
                .findByOwnerIdOrderByUpdatedAtDesc(
                        ownerId
                )
                .stream()
                .map(work ->
                        new WorkSummary(
                                work.getId(),
                                work.getTitle(),
                                work.getType(),
                                work.getGenres(),
                                work.getCoverStorageKey() != null
                        )
                )
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkDetails findCreatorWorkDetails(
            UUID userId,
            UUID workId
    ) {
        Work work = repository.findById(workId)
                .orElseThrow(() ->
                        new WorkNotFoundException(workId)
                );

        if (!work.getOwnerId().equals(userId)) {
            throw new WorkAccessDeniedException();
        }

        return new WorkDetails(
                work.getId(),
                work.getTitle(),
                work.getDescription(),
                work.getType(),
                work.getReadingDirection(),
                work.getPresentationMode(),
                work.getGenres(),
                work.getCoverStorageKey() != null
        );
    }

    @Transactional(readOnly = true)
    public WorkCoverContent findCreatorWorkCover(
            UUID userId,
            UUID workId
    ) {
        Work work = repository.findById(workId)
                .orElseThrow(() ->
                        new WorkNotFoundException(workId)
                );

        if (!work.getOwnerId().equals(userId)) {
            throw new WorkAccessDeniedException();
        }

        if (work.getCoverStorageKey() == null) {
            throw new WorkCoverNotFoundException(
                    workId
            );
        }

        byte[] content =
                binaryStorage.read(
                        work.getCoverStorageKey()
                );

        return new WorkCoverContent(
                work.getCoverContentType(),
                content
        );
    }
}
