package metopa.publication.internal.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocalPageStorageTests {

    @TempDir
    Path tempDirectory;

    @Test
    void shouldStorePageContent()
            throws Exception {

        LocalPageStorage storage =
                new LocalPageStorage(
                        tempDirectory.toString()
                );

        UUID installmentId =
                UUID.randomUUID();

        byte[] content =
                "page-content".getBytes();

        String storageKey =
                storage.store(
                        installmentId,
                        content
                );

        assertThat(storageKey)
                .startsWith(
                        "installments/"
                                + installmentId
                                + "/"
                );

        Path storedFile =
                tempDirectory.resolve(storageKey);

        assertThat(storedFile)
                .exists();

        assertThat(
                Files.readAllBytes(storedFile)
        ).isEqualTo(content);
    }

    @Test
    void shouldReadStoredPageContent() {
        LocalPageStorage storage =
                new LocalPageStorage(
                        tempDirectory.toString()
                );

        UUID installmentId =
                UUID.randomUUID();

        byte[] content =
                "page-content".getBytes();

        String storageKey =
                storage.store(
                        installmentId,
                        content
                );

        byte[] storedContent =
                storage.read(storageKey);

        assertThat(storedContent)
                .isEqualTo(content);
    }
}