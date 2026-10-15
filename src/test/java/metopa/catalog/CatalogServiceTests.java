package metopa.catalog;

import metopa.catalog.internal.Work;
import metopa.catalog.internal.WorkRepository;
import metopa.media.BinaryStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTests {

    @Mock
    private WorkRepository repository;

    @Mock
    private BinaryStorage binaryStorage;

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

        work.setGenres(
                Set.of(
                        Genre.SCI_FI,
                        Genre.ADVENTURE
                )
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

        assertThat(summary.genres())
                .containsExactlyInAnyOrder(
                        Genre.SCI_FI,
                        Genre.ADVENTURE
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

        work.setGenres(
                Set.of(
                        Genre.SCI_FI,
                        Genre.ADVENTURE
                )
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

        assertThat(details.genres())
                .containsExactlyInAnyOrder(
                        Genre.SCI_FI,
                        Genre.ADVENTURE
                );
    }

    @Test
    void shouldSetCoverForWorkOwner() {
        UUID userId = UUID.randomUUID();

        Work work =
                new Work(
                        userId,
                        "Covered Work",
                        null,
                        WorkType.COMIC,
                        ReadingDirection.LEFT_TO_RIGHT,
                        PresentationMode.SINGLE_PAGE
                );

        byte[] content =
                "cover-content".getBytes();

        String storageKey =
                "covers/"
                        + work.getId()
                        + "/cover-file";

        when(repository.findById(work.getId()))
                .thenReturn(Optional.of(work));

        when(
                binaryStorage.store(
                        "covers",
                        work.getId(),
                        content
                )
        ).thenReturn(storageKey);

        catalogService.setCover(
                userId,
                work.getId(),
                "image/webp",
                content
        );

        assertThat(work.getCoverStorageKey())
                .isEqualTo(storageKey);

        assertThat(work.getCoverContentType())
                .isEqualTo("image/webp");

        verify(binaryStorage)
                .store(
                        "covers",
                        work.getId(),
                        content
                );
    }

    @Test
    void shouldRejectCoverFromUserWhoDoesNotOwnWork() {
        UUID ownerId = UUID.randomUUID();
        UUID anotherUserId = UUID.randomUUID();

        Work work =
                new Work(
                        ownerId,
                        "Covered Work",
                        null,
                        WorkType.COMIC,
                        ReadingDirection.LEFT_TO_RIGHT,
                        PresentationMode.SINGLE_PAGE
                );

        byte[] content =
                "cover-content".getBytes();

        when(repository.findById(work.getId()))
                .thenReturn(Optional.of(work));

        assertThatThrownBy(() ->
                catalogService.setCover(
                        anotherUserId,
                        work.getId(),
                        "image/webp",
                        content
                )
        ).isInstanceOf(
                WorkAccessDeniedException.class
        );

        verify(
                binaryStorage,
                never()
        ).store(
                any(String.class),
                any(UUID.class),
                any(byte[].class)
        );

        assertThat(work.getCoverStorageKey())
                .isNull();

        assertThat(work.getCoverContentType())
                .isNull();
    }

    @Test
    void shouldReadWorkCover() {
        UUID ownerId = UUID.randomUUID();

        Work work =
                new Work(
                        ownerId,
                        "Covered Work",
                        null,
                        WorkType.COMIC,
                        ReadingDirection.LEFT_TO_RIGHT,
                        PresentationMode.SINGLE_PAGE
                );

        String storageKey =
                "covers/"
                        + work.getId()
                        + "/cover-file";

        work.setCover(
                storageKey,
                "image/webp"
        );

        byte[] content =
                "cover-content".getBytes();

        when(repository.findById(work.getId()))
                .thenReturn(Optional.of(work));

        when(binaryStorage.read(storageKey))
                .thenReturn(content);

        WorkCoverContent result =
                catalogService.findWorkCover(
                        work.getId()
                );

        assertThat(result.contentType())
                .isEqualTo("image/webp");

        assertThat(result.content())
                .isEqualTo(content);

        verify(binaryStorage)
                .read(storageKey);
    }

    @Test
    void shouldRejectWorkWithoutCover() {
        UUID ownerId = UUID.randomUUID();

        Work work =
                new Work(
                        ownerId,
                        "Work Without Cover",
                        null,
                        WorkType.COMIC,
                        ReadingDirection.LEFT_TO_RIGHT,
                        PresentationMode.SINGLE_PAGE
                );

        when(repository.findById(work.getId()))
                .thenReturn(Optional.of(work));

        assertThatThrownBy(() ->
                catalogService.findWorkCover(
                        work.getId()
                )
        ).isInstanceOf(
                WorkCoverNotFoundException.class
        );

        verify(
                binaryStorage,
                never()
        ).read(any(String.class));
    }
}