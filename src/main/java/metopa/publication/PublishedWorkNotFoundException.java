package metopa.publication;

import java.util.UUID;

public class PublishedWorkNotFoundException
        extends RuntimeException {

    public PublishedWorkNotFoundException(UUID workId) {
        super(
                "Published work not found: " + workId
        );
    }
}