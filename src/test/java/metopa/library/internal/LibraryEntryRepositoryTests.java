package metopa.library.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class LibraryEntryRepositoryTests {

    @Autowired
    private LibraryEntryRepository repository;

    @Test
    void shouldPersistLibraryEntry() {
        UUID userId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();

        LibraryEntry entry =
                new LibraryEntry(
                        userId,
                        workId
                );

        LibraryEntry saved =
                repository.saveAndFlush(entry);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId())
                .isEqualTo(userId);
        assertThat(saved.getWorkId())
                .isEqualTo(workId);
        assertThat(saved.getAddedAt())
                .isNotNull();
    }
}