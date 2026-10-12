package metopa.publication;

import java.util.UUID;

public class PublishedWorkCoverNotFoundException
        extends RuntimeException {

    public PublishedWorkCoverNotFoundException(
            UUID workId
    ) {
        super(
                "Published work cover not found: "
                        + workId
        );
    }
}