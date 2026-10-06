package metopa.publication.internal.storage;

import java.util.UUID;

public interface PageStorage {

    String store(
            UUID installmentId,
            byte[] content
    );

    byte[] read(String storageKey);
}