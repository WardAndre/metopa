package metopa.publication.internal;

import metopa.catalog.WorkDeletionGuard;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class PublicationWorkDeletionGuard
        implements WorkDeletionGuard {

    private final InstallmentRepository repository;

    PublicationWorkDeletionGuard(
            InstallmentRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public boolean hasInstallments(UUID workId) {
        return repository.existsByWorkId(workId);
    }
}
