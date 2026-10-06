package metopa.publication;

import java.util.UUID;

public class PublishedPageNotFoundException
        extends RuntimeException {

    public PublishedPageNotFoundException(
            UUID pageId
    ) {
        super(
                "Published page not found: "
                        + pageId
        );
    }
}