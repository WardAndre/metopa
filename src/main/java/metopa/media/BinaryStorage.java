package metopa.media;

import java.util.UUID;

public interface BinaryStorage {

    String store(
            String namespace,
            UUID ownerId,
            byte[] content
    );

    byte[] read(String storageKey);
}