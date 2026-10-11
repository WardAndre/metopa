package metopa.media.internal;

import metopa.media.BinaryStorage;
import metopa.media.BinaryStorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Component
class LocalBinaryStorage
        implements BinaryStorage {

    private final Path root;

    LocalBinaryStorage(
            @Value("${metopa.storage.root:storage}")
            String root
    ) {
        this.root = Path.of(root)
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public String store(
            String namespace,
            UUID ownerId,
            byte[] content
    ) {
        String storageKey =
                namespace
                        + "/"
                        + ownerId
                        + "/"
                        + UUID.randomUUID();

        Path target =
                resolve(storageKey);

        try {
            Files.createDirectories(
                    target.getParent()
            );

            Files.write(
                    target,
                    content,
                    StandardOpenOption.CREATE_NEW
            );

            return storageKey;

        } catch (IOException exception) {
            throw new BinaryStorageException(
                    "Failed to store binary content.",
                    exception
            );
        }
    }

    @Override
    public byte[] read(String storageKey) {
        Path target =
                resolve(storageKey);

        try {
            return Files.readAllBytes(target);

        } catch (IOException exception) {
            throw new BinaryStorageException(
                    "Failed to read binary content.",
                    exception
            );
        }
    }

    private Path resolve(String storageKey) {
        Path target =
                root.resolve(storageKey)
                        .normalize();

        if (!target.startsWith(root)) {
            throw new IllegalArgumentException(
                    "Invalid storage key."
            );
        }

        return target;
    }
}