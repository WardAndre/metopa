package metopa.reading;

import metopa.publication.PublicationService;
import metopa.publication.PublishedPageReference;
import metopa.reading.internal.ReadingProgress;
import metopa.reading.internal.ReadingProgressRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadingServiceTests {

    @Mock
    private ReadingProgressRepository repository;

    @Mock
    private PublicationService publicationService;

    @Test
    void shouldCreateReadingProgressWhenNoneExists() {
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();

        ReadingService service =
                new ReadingService(
                        repository,
                        publicationService
                );

        when(
                publicationService
                        .findPublishedPageReference(pageId)
        ).thenReturn(
                new PublishedPageReference(
                        pageId,
                        installmentId,
                        workId
                )
        );

        when(
                repository.findByUserIdAndWorkId(
                        userId,
                        workId
                )
        ).thenReturn(Optional.empty());

        when(repository.save(any(ReadingProgress.class)))
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(0)
                );

        UUID progressId =
                service.saveProgress(
                        userId,
                        pageId
                );

        assertThat(progressId).isNotNull();

        verify(repository)
                .save(any(ReadingProgress.class));
    }

    @Test
    void shouldUpdateExistingReadingProgress() {
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        UUID oldInstallmentId = UUID.randomUUID();
        UUID oldPageId = UUID.randomUUID();

        UUID newInstallmentId = UUID.randomUUID();
        UUID newPageId = UUID.randomUUID();

        ReadingProgress existing =
                new ReadingProgress(
                        userId,
                        workId,
                        oldInstallmentId,
                        oldPageId
                );

        ReadingService service =
                new ReadingService(
                        repository,
                        publicationService
                );

        when(
                publicationService
                        .findPublishedPageReference(
                                newPageId
                        )
        ).thenReturn(
                new PublishedPageReference(
                        newPageId,
                        newInstallmentId,
                        workId
                )
        );

        when(
                repository.findByUserIdAndWorkId(
                        userId,
                        workId
                )
        ).thenReturn(
                Optional.of(existing)
        );

        when(repository.save(existing))
                .thenReturn(existing);

        UUID progressId =
                service.saveProgress(
                        userId,
                        newPageId
                );

        assertThat(progressId)
                .isEqualTo(existing.getId());

        assertThat(existing.getInstallmentId())
                .isEqualTo(newInstallmentId);

        assertThat(existing.getPageId())
                .isEqualTo(newPageId);

        verify(repository)
                .save(existing);
    }

    @Test
    void shouldFindReadingProgress() {
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();

        ReadingProgress progress =
                new ReadingProgress(
                        userId,
                        workId,
                        installmentId,
                        pageId
                );

        ReadingService service =
                new ReadingService(
                        repository,
                        publicationService
                );

        when(
                repository.findByUserIdAndWorkId(
                        userId,
                        workId
                )
        ).thenReturn(
                Optional.of(progress)
        );

        Optional<ReadingProgressView> result =
                service.findProgress(
                        userId,
                        workId
                );

        assertThat(result).isPresent();

        ReadingProgressView view =
                result.orElseThrow();

        assertThat(view.id())
                .isEqualTo(progress.getId());

        assertThat(view.workId())
                .isEqualTo(workId);

        assertThat(view.installmentId())
                .isEqualTo(installmentId);

        assertThat(view.pageId())
                .isEqualTo(pageId);

        assertThat(view.updatedAt())
                .isEqualTo(progress.getUpdatedAt());
    }

    @Test
    void shouldReturnEmptyWhenReadingProgressDoesNotExist() {
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        ReadingService service =
                new ReadingService(
                        repository,
                        publicationService
                );

        when(
                repository.findByUserIdAndWorkId(
                        userId,
                        workId
                )
        ).thenReturn(Optional.empty());

        Optional<ReadingProgressView> result =
                service.findProgress(
                        userId,
                        workId
                );

        assertThat(result).isEmpty();
    }
}