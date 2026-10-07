package metopa.library;

import metopa.library.internal.LibraryEntry;
import metopa.library.internal.LibraryEntryRepository;
import metopa.catalog.CatalogService;
import metopa.catalog.WorkSummary;
import metopa.catalog.WorkType;
import metopa.publication.PublicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LibraryServiceTests {

    @Mock
    private LibraryEntryRepository repository;

    @Mock
    private PublicationService publicationService;

    @InjectMocks
    private LibraryService libraryService;

    @Mock
    private CatalogService catalogService;

    @Test
    void shouldAddPublishedWorkToLibrary() {
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        when(
                publicationService
                        .hasPublishedContent(workId)
        ).thenReturn(true);

        when(
                repository.existsByUserIdAndWorkId(
                        userId,
                        workId
                )
        ).thenReturn(false);

        when(
                repository.save(
                        any(LibraryEntry.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        UUID entryId =
                libraryService.addWork(
                        userId,
                        workId
                );

        assertThat(entryId).isNotNull();

        verify(repository)
                .save(any(LibraryEntry.class));
    }

    @Test
    void shouldRejectWorkWithoutPublishedContent() {
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        when(
                publicationService
                        .hasPublishedContent(workId)
        ).thenReturn(false);

        assertThatThrownBy(() ->
                libraryService.addWork(
                        userId,
                        workId
                )
        ).isInstanceOf(
                WorkNotAvailableForLibraryException.class
        );

        verify(
                repository,
                never()
        ).save(any());
    }

    @Test
    void shouldRejectDuplicateLibraryEntry() {
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        when(
                publicationService
                        .hasPublishedContent(workId)
        ).thenReturn(true);

        when(
                repository.existsByUserIdAndWorkId(
                        userId,
                        workId
                )
        ).thenReturn(true);

        assertThatThrownBy(() ->
                libraryService.addWork(
                        userId,
                        workId
                )
        ).isInstanceOf(
                LibraryEntryAlreadyExistsException.class
        );

        verify(
                repository,
                never()
        ).save(any());
    }

    @Test
    void shouldReturnUsersLibrary() {
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        LibraryEntry entry =
                new LibraryEntry(
                        userId,
                        workId
                );

        when(
                repository.findByUserIdOrderByAddedAtDesc(
                        userId
                )
        ).thenReturn(
                java.util.List.of(entry)
        );

        when(
                catalogService.findWorkSummary(workId)
        ).thenReturn(
                new WorkSummary(
                        workId,
                        "Metopa Origins",
                        WorkType.GRAPHIC_NOVEL
                )
        );

        var result =
                libraryService.findLibrary(userId);

        assertThat(result).hasSize(1);

        LibraryEntryView item =
                result.getFirst();

        assertThat(item.id())
                .isEqualTo(entry.getId());

        assertThat(item.workId())
                .isEqualTo(workId);

        assertThat(item.title())
                .isEqualTo("Metopa Origins");

        assertThat(item.type())
                .isEqualTo(
                        WorkType.GRAPHIC_NOVEL
                );

        assertThat(item.addedAt())
                .isEqualTo(entry.getAddedAt());
    }

    @Test
    void shouldReturnEmptyLibrary() {
        UUID userId = UUID.randomUUID();

        when(
                repository.findByUserIdOrderByAddedAtDesc(
                        userId
                )
        ).thenReturn(
                java.util.List.of()
        );

        var result =
                libraryService.findLibrary(userId);

        assertThat(result).isEmpty();
    }
}