package metopa.publication;

import java.util.UUID;

public record PublishedPageReference(
        UUID pageId,
        UUID installmentId,
        UUID workId
) {
}