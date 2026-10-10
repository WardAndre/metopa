package metopa.catalog;

import metopa.catalog.internal.Work;
import metopa.catalog.internal.WorkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
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

    @Test
    void shouldFindWorkReference() {
        UUID ownerId = UUID.randomUUID();

        Work work = new Work(
                ownerId,
                "Metopa Origins",
                null,
                WorkType.GRAPHIC_NOVEL,
                ReadingDirection.LEFT_TO_RIGHT,
                PresentationMode.SINGLE_PAGE
        );

        when(repository.findById(work.getId()))
                .thenReturn(Optional.of(work));

        WorkReference reference =
                catalogService.findWork(work.getId());

        assertThat(reference.id()).isEqualTo(work.getId());
        assertThat(reference.ownerId()).isEqualTo(ownerId);
    }

    @Test
    void shouldFindWorkSummary() {
        UUID ownerId = UUID.randomUUID();

        Work work = new Work(
                ownerId,
                "Metopa Origins",
                null,
                WorkType.GRAPHIC_NOVEL,
                ReadingDirection.LEFT_TO_RIGHT,
                PresentationMode.SINGLE_PAGE
        );

        when(repository.findById(work.getId()))
                .thenReturn(Optional.of(work));

        WorkSummary summary =
                catalogService.findWorkSummary(
                        work.getId()
                );

        assertThat(summary.id())
                .isEqualTo(work.getId());

        assertThat(summary.title())
                .isEqualTo("Metopa Origins");

        assertThat(summary.type())
                .isEqualTo(
                        WorkType.GRAPHIC_NOVEL
                );
    }

    @Test
    void shouldFindWorkDetails() {
        UUID ownerId = UUID.randomUUID();

        Work work = new Work(
                ownerId,
                "Metopa Origins",
                "A science fiction graphic novel.",
                WorkType.GRAPHIC_NOVEL,
                ReadingDirection.LEFT_TO_RIGHT,
                PresentationMode.SINGLE_PAGE
        );

        when(repository.findById(work.getId()))
                .thenReturn(Optional.of(work));

        WorkDetails details =
                catalogService.findWorkDetails(
                        work.getId()
                );

        assertThat(details.id())
                .isEqualTo(work.getId());

        assertThat(details.title())
                .isEqualTo("Metopa Origins");

        assertThat(details.description())
                .isEqualTo(
                        "A science fiction graphic novel."
                );

        assertThat(details.type())
                .isEqualTo(
                        WorkType.GRAPHIC_NOVEL
                );

        assertThat(details.readingDirection())
                .isEqualTo(
                        ReadingDirection.LEFT_TO_RIGHT
                );

        assertThat(details.presentationMode())
                .isEqualTo(
                        PresentationMode.SINGLE_PAGE
                );
    }
}