package metopa.catalog;

import jakarta.validation.Valid;
import metopa.catalog.internal.Work;
import metopa.catalog.internal.WorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

@Service
@Validated
public class CatalogService {

    private final WorkRepository repository;

    public CatalogService(WorkRepository repository) {
        this.repository = repository;
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

        Work saved = repository.save(work);

        return saved.getId();
    }
}