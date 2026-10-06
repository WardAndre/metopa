package metopa.publication.internal.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Component
public class LocalPageStorage
        implements PageStorage {

    private final Path root;

    public LocalPageStorage(
            @Value("${metopa.storage.root:storage}")
            String root
    ) {
        this.root = Path.of(root)
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public String store(
            UUID installmentId,
            byte[] content
    ) {
        String storageKey =
                "installments/"
                        + installmentId
                        + "/"
                        + UUID.randomUUID();

        Path target = root
                .resolve(storageKey)
                .normalize();

        if (!target.startsWith(root)) {
            throw new IllegalStateException(
                    "Invalid storage path."
            );
        }

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
            throw new PageStorageException(
                    "Could not store page content.",
                    exception
            );
        }
    }
    @Override
    public byte[] read(String storageKey) {
        Path source = root
                .resolve(storageKey)
                .normalize();

        if (!source.startsWith(root)) {
            throw new IllegalStateException(
                    "Invalid storage path."
            );
        }

        try {
            return Files.readAllBytes(source);
        } catch (IOException exception) {
            throw new PageStorageException(
                    "Could not read page content.",
                    exception
            );
        }
    }
}