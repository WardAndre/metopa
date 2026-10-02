package metopa.catalog;

import metopa.catalog.internal.Work;
import metopa.catalog.internal.WorkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTests {

    @Mock
    private WorkRepository repository;

    @InjectMocks
    private CatalogService catalogService;

    @Test
    void shouldCreateWork() {
        UUID ownerId = UUID.randomUUID();

        CreateWorkCommand command = new CreateWorkCommand(
                ownerId,
                "Metopa Origins",
                "A graphic novel.",
                WorkType.GRAPHIC_NOVEL,
                ReadingDirection.LEFT_TO_RIGHT,
                PresentationMode.SINGLE_PAGE
        );

        when(repository.save(any(Work.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UUID workId = catalogService.createWork(command);

        assertThat(workId).isNotNull();

        verify(repository).save(any(Work.class));
    }
}