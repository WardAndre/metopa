package metopa.library;

import metopa.library.internal.LibraryEntry;
import metopa.library.internal.LibraryEntryRepository;
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
}