package metopa.reading.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ReadingProgressRepositoryTests {

    @Autowired
    private ReadingProgressRepository repository;

    @Test
    void shouldPersistReadingProgress() {
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

        ReadingProgress saved =
                repository.saveAndFlush(progress);

        ReadingProgress found =
                repository.findByUserIdAndWorkId(
                                userId,
                                workId
                        )
                        .orElseThrow();

        assertThat(found.getId())
                .isEqualTo(saved.getId());

        assertThat(found.getUserId())
                .isEqualTo(userId);

        assertThat(found.getWorkId())
                .isEqualTo(workId);

        assertThat(found.getInstallmentId())
                .isEqualTo(installmentId);

        assertThat(found.getPageId())
                .isEqualTo(pageId);

        assertThat(found.getUpdatedAt())
                .isNotNull();
    }
}