package metopa.media.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalBinaryStorageTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldStoreAndReadBinaryContent() {
        LocalBinaryStorage storage =
                new LocalBinaryStorage(
                        tempDir.toString()
                );

        UUID ownerId = UUID.randomUUID();

        byte[] content =
                "binary-content".getBytes();

        String storageKey =
                storage.store(
                        "installments",
                        ownerId,
                        content
                );

        assertThat(storageKey)
                .startsWith(
                        "installments/"
                                + ownerId
                                + "/"
                );

        byte[] storedContent =
                storage.read(storageKey);

        assertThat(storedContent)
                .isEqualTo(content);
    }

    @Test
    void shouldRejectStorageKeyOutsideRoot() {
        LocalBinaryStorage storage =
                new LocalBinaryStorage(
                        tempDir.toString()
                );

        assertThatThrownBy(() ->
                storage.read(
                        "../outside-file"
                )
        ).isInstanceOf(
                IllegalArgumentException.class
        );
    }

    @Test
    void shouldDeleteBinaryContent() {
        LocalBinaryStorage storage =
                new LocalBinaryStorage(
                        tempDir.toString()
                );

        UUID ownerId = UUID.randomUUID();

        String storageKey =
                storage.store(
                        "installments",
                        ownerId,
                        "binary-content".getBytes()
                );

        storage.delete(storageKey);

        assertThatThrownBy(() ->
                storage.read(storageKey)
        ).isInstanceOf(
                metopa.media.BinaryStorageException.class
        );
    }

    @Test
    void shouldRejectDeleteStorageKeyOutsideRoot() {
        LocalBinaryStorage storage =
                new LocalBinaryStorage(
                        tempDir.toString()
                );

        assertThatThrownBy(() ->
                storage.delete(
                        "../outside-file"
                )
        ).isInstanceOf(
                IllegalArgumentException.class
        );
    }
}