package metopa.identity;

import java.util.UUID;

public record UserReference(
        UUID id,
        String username
) {
}