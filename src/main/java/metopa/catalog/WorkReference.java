package metopa.catalog;

import java.util.UUID;

public record WorkReference(
        UUID id,
        UUID ownerId
) {
}